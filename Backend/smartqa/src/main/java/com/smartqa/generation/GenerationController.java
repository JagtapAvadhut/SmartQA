package com.smartqa.generation;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.testcase.TestCaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
public class GenerationController {

    private static final Logger log = LoggerFactory.getLogger(GenerationController.class);

    private final GenerationService generationService;
    private final ProgressEventHub eventHub;
    private final GenerationRunRegistry runRegistry;

    public GenerationController(GenerationService generationService, ProgressEventHub eventHub, GenerationRunRegistry runRegistry) {
        this.generationService = generationService;
        this.eventHub = eventHub;
        this.runRegistry = runRegistry;
    }

    @PostMapping("/api/test-cases/{id}/generate")
    public Mono<ApiResponse<GenerationRun>> generate(@PathVariable UUID id) {
        TraceLogger.info("CONTROLLER", "ENTER", "generate (async)", TraceMeta.of(
                "testCaseId", id.toString(),
                "operation", "GENERATE_TEST"));

        if (runRegistry.isRunning(id)) {
            GenerationRun existing = runRegistry.getByTestCase(id);
            return Mono.just(ApiResponse.ok("Generation already running", existing));
        }

        GenerationRun run = GenerationRun.start(id);
        runRegistry.register(run);

        generationService.generateAsync(id, run.id())
                .timeout(Duration.ofMinutes(45))
                .doFinally(signal -> {
                    GenerationRun current = runRegistry.get(run.id());
                    if (current != null && !current.isTerminal()) {
                        runRegistry.update(current.fail("Generation ended without terminal state: " + signal));
                    }
                })
                .subscribe(
                        result -> {
                            log.info("generation_completed testCaseId={} runId={}", id, run.id());
                            runRegistry.update(run.complete("SUCCESS"));
                        },
                        error -> {
                            log.error("generation_failed testCaseId={} runId={}", id, run.id(), error);
                            String message = error.getMessage() != null ? error.getMessage() : "Generation failed";
                            runRegistry.update(run.fail(message));
                        }
                );

        return Mono.just(ApiResponse.ok("Generation started", run));
    }

    @GetMapping("/api/generation-runs/{runId}")
    public Mono<ApiResponse<GenerationRun>> getGenerationRun(@PathVariable UUID runId) {
        GenerationRun run = runRegistry.get(runId);
        if (run == null) {
            return Mono.just(ApiResponse.fail("Generation run not found", "NOT_FOUND"));
        }
        return Mono.just(ApiResponse.ok("Generation run status", run));
    }

    @GetMapping("/api/test-cases/{id}/generation/latest")
    public Mono<ApiResponse<GenerationRun>> getLatestGenerationRun(@PathVariable UUID id) {
        GenerationRun run = runRegistry.getByTestCase(id);
        if (run == null) {
            return Mono.just(ApiResponse.fail("No generation run found", "NOT_FOUND"));
        }
        return Mono.just(ApiResponse.ok("Latest generation run", run));
    }

    @PutMapping("/api/test-cases/{id}/code")
    public Mono<ApiResponse<TestCaseResponse>> saveCode(
            @PathVariable UUID id,
            @RequestBody GeneratedCodeRequest request) {
        int codeLength = request == null || request.generatedCode() == null ? 0 : request.generatedCode().length();
        TraceLogger.info("CONTROLLER", "ENTER", "saveCode", TraceMeta.of(
                "testCaseId", id.toString(),
                "codeLength", codeLength));
        return generationService.saveCode(id, request)
                .map(item -> ApiResponse.ok("Generated code saved", item));
    }

    @GetMapping(path = "/api/test-cases/{id}/generation/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ProgressEvent>> stream(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(value = "lastEventId", required = false) Long lastEventId) {
        TraceLogger.info("SSE", "SSE_SUBSCRIBE", "Generation SSE subscribed", TraceMeta.of("testCaseId", id.toString()));
        Flux<ServerSentEvent<ProgressEvent>> events = eventHub.stream(ProgressEventHub.generationChannel(id), lastEventId)
                .map(event -> ServerSentEvent.builder(event)
                        .id(event.eventId() == null ? null : String.valueOf(event.eventId()))
                        .event(event.type())
                        .build());
        Flux<ServerSentEvent<ProgressEvent>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<ProgressEvent>builder().comment("ping").build());
        return Flux.merge(events, heartbeat);
    }
}
