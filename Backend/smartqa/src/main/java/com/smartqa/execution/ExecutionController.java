package com.smartqa.execution;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.execution.cancel.ExecutionCancellationRegistry;
import com.smartqa.execution.event.ExecutionEvent;
import com.smartqa.execution.event.ExecutionEventStore;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
public class ExecutionController {

    private final ExecutionService executionService;
    private final ProgressEventHub eventHub;
    private final ExecutionEventStore eventStore;
    private final ExecutionCancellationRegistry cancellationRegistry;

    public ExecutionController(ExecutionService executionService, ProgressEventHub eventHub,
                               ExecutionEventStore eventStore, ExecutionCancellationRegistry cancellationRegistry) {
        this.executionService = executionService;
        this.eventHub = eventHub;
        this.eventStore = eventStore;
        this.cancellationRegistry = cancellationRegistry;
    }

    @PostMapping("/api/test-cases/{id}/execute")
    public Mono<ApiResponse<ExecutionRunResponse>> execute(@PathVariable UUID id,
                                                            @RequestBody(required = false) ExecutionStartRequest request) {
        long started = System.nanoTime();
        TraceLogger.info("CONTROLLER", "ENTER", "execute", TraceMeta.of(
                "testCaseId", id.toString(),
                "operation", "EXECUTE_TEST"));
        return executionService.execute(id, request)
                .doOnSuccess(item -> TraceLogger.info("CONTROLLER", "EXIT", "execute",
                        (System.nanoTime() - started) / 1_000_000,
                        TraceMeta.of("status", "SUCCESS", "testCaseId", id.toString(), "runId", item.id())))
                .doOnError(error -> TraceLogger.error("CONTROLLER", "EXIT", "execute failed", error,
                        (System.nanoTime() - started) / 1_000_000,
                        TraceMeta.of("status", "FAILED", "testCaseId", id.toString())))
                .map(item -> ApiResponse.ok("Execution started", item));
    }

    @GetMapping("/api/execution-runs/{id}")
    public Mono<ApiResponse<ExecutionRunResponse>> get(@PathVariable UUID id) {
        return executionService.get(id)
                .map(item -> ApiResponse.ok("Execution run fetched", item));
    }

    @PostMapping("/api/execution-runs/{id}/stop")
    public Mono<ApiResponse<ExecutionRunResponse>> stop(@PathVariable UUID id) {
        return executionService.get(id)
                .flatMap(run -> {
                    if (!"RUNNING".equals(run.status())) {
                        return Mono.error(new SmartQaException(ErrorCode.CONFLICT,
                                "Execution run is not running (status=" + run.status() + ")"));
                    }
                    return executionService.stop(id);
                })
                .map(item -> ApiResponse.ok("Stop requested", item));
    }

    @GetMapping(path = "/api/execution-runs/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ProgressEvent>> stream(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(value = "lastEventId", required = false) Long lastEventId) {
        TraceLogger.info("SSE", "SSE_SUBSCRIBE", "Execution SSE subscribed", TraceMeta.of("runId", id.toString()));
        Flux<ServerSentEvent<ProgressEvent>> events = eventHub.stream(ProgressEventHub.executionChannel(id), lastEventId)
                .map(event -> ServerSentEvent.builder(event)
                        .id(event.eventId() == null ? null : String.valueOf(event.eventId()))
                        .event(event.type())
                        .build());
        Flux<ServerSentEvent<ProgressEvent>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<ProgressEvent>builder().comment("ping").build());
        return Flux.merge(events, heartbeat);
    }

    @GetMapping("/api/execution-runs/{id}/events")
    public Mono<ApiResponse<List<ExecutionEvent>>> events(@PathVariable UUID id) {
        List<ExecutionEvent> events = eventStore.get(id);
        return Mono.just(ApiResponse.ok("Events fetched", events));
    }

    @GetMapping("/api/execution-runs/{id}/events/{stepNumber}")
    public Mono<ApiResponse<List<ExecutionEvent>>> eventsByStep(@PathVariable UUID id, @PathVariable int stepNumber) {
        List<ExecutionEvent> events = eventStore.getByStep(id, stepNumber);
        return Mono.just(ApiResponse.ok("Step events fetched", events));
    }
}
