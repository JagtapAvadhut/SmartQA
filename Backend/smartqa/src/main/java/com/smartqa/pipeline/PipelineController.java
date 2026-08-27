package com.smartqa.pipeline;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
public class PipelineController {

    private final PipelineService pipelineService;
    private final ProgressEventHub eventHub;

    public PipelineController(PipelineService pipelineService, ProgressEventHub eventHub) {
        this.pipelineService = pipelineService;
        this.eventHub = eventHub;
    }

    @PostMapping("/api/workspace/generate-and-validate")
    public Mono<ApiResponse<PipelineRunResponse>> generateAndValidate(@RequestBody PipelineStartRequest request) {
        TraceLogger.info("CONTROLLER", "ENTER", "generateAndValidate", TraceMeta.of(
                "operation", "GENERATE_AND_VALIDATE",
                "hasStructuredSteps", request != null && request.structuredSteps() != null
                        && !request.structuredSteps().isEmpty()
        ));
        return pipelineService.start(request)
                .map(run -> ApiResponse.ok("Generate & Validate pipeline started", run));
    }

    @GetMapping("/api/pipelines/{id}")
    public Mono<ApiResponse<PipelineRunResponse>> get(@PathVariable UUID id) {
        return pipelineService.get(id)
                .map(run -> ApiResponse.ok("Pipeline status", run));
    }

    @GetMapping("/api/test-cases/{testCaseId}/pipelines/latest")
    public Mono<ApiResponse<PipelineRunResponse>> latest(@PathVariable UUID testCaseId) {
        return pipelineService.getLatestByTestCaseId(testCaseId)
                .map(run -> ApiResponse.ok("Latest pipeline", run))
                .switchIfEmpty(Mono.just(ApiResponse.ok("No pipeline for test case", null)));
    }

    @PostMapping("/api/pipelines/{id}/stop")
    public Mono<ApiResponse<PipelineRunResponse>> stop(@PathVariable UUID id) {
        return pipelineService.stop(id)
                .map(run -> ApiResponse.ok("Pipeline stop requested", run));
    }

    @PostMapping("/api/pipelines/{id}/fix-and-rebuild")
    public Mono<ApiResponse<SourceFixProposal>> fixAndRebuild(@PathVariable UUID id) {
        TraceLogger.info("CONTROLLER", "ENTER", "fixAndRebuild", TraceMeta.of("pipelineId", id.toString()));
        return pipelineService.requestFixAndRebuild(id)
                .map(proposal -> ApiResponse.ok("Fix & Rebuild requested", proposal));
    }

    @GetMapping(path = "/api/pipelines/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ProgressEvent>> stream(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(value = "lastEventId", required = false) Long lastEventId) {
        TraceLogger.info("SSE", "SSE_SUBSCRIBE", "Pipeline SSE subscribed", TraceMeta.of("pipelineId", id.toString()));
        Flux<ProgressEvent> source = eventHub.stream(ProgressEventHub.pipelineChannel(id), lastEventId);
        Flux<ServerSentEvent<ProgressEvent>> events = source
                .map(event -> ServerSentEvent.builder(event)
                        .id(event.eventId() == null ? null : String.valueOf(event.eventId()))
                        .event(event.type())
                        .build());
        Flux<ServerSentEvent<ProgressEvent>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<ProgressEvent>builder().comment("ping").build());
        return Flux.merge(events, heartbeat);
    }
}
