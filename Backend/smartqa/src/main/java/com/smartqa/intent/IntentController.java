package com.smartqa.intent;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.testcase.TestCaseResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
public class IntentController {

    private final IntentService intentService;

    public IntentController(IntentService intentService) {
        this.intentService = intentService;
    }

    @PostMapping("/api/test-cases/{id}/understand")
    public Mono<ApiResponse<TestCaseResponse>> understand(@PathVariable UUID id) {
        return Mono.deferContextual(ctx -> {
            String traceId = TraceContext.from(ctx);
            TraceContext.set(traceId);
            long started = System.nanoTime();
            TraceLogger.info("CONTROLLER", "ENTER", "understand", TraceMeta.of(
                    "testCaseId", id.toString(),
                    "operation", "ANALYZE"));
            return intentService.understand(id)
                    .doOnEach(signal -> TraceContext.set(traceId))
                    .doOnSuccess(item -> TraceLogger.info("CONTROLLER", "CONTROLLER_EXIT", "understand",
                            (System.nanoTime() - started) / 1_000_000,
                            TraceMeta.of("status", "SUCCESS", "testCaseId", id.toString())))
                    .doOnError(error -> TraceLogger.error("CONTROLLER", "CONTROLLER_EXIT", "understand failed", error,
                            (System.nanoTime() - started) / 1_000_000,
                            TraceMeta.of("status", "FAILED", "testCaseId", id.toString())))
                    .map(item -> ApiResponse.ok("Intent analyzed", item));
        });
    }

    @PostMapping("/api/test-cases/{id}/clarify")
    public Mono<ApiResponse<TestCaseResponse>> clarify(
            @PathVariable UUID id,
            @Valid @RequestBody ClarifyRequest request) {
        long started = System.nanoTime();
        int answers = request == null || request.answers() == null ? 0 : request.answers().size();
        TraceLogger.info("CONTROLLER", "ENTER", "clarify", TraceMeta.of(
                "testCaseId", id.toString(),
                "answers", answers));
        return intentService.clarify(id, request)
                .doOnSuccess(item -> TraceLogger.info("CONTROLLER", "EXIT", "clarify",
                        (System.nanoTime() - started) / 1_000_000,
                        TraceMeta.of("status", "SUCCESS")))
                .doOnError(error -> TraceLogger.error("CONTROLLER", "EXIT", "clarify failed", error,
                        (System.nanoTime() - started) / 1_000_000,
                        TraceMeta.of("status", "FAILED")))
                .map(item -> ApiResponse.ok("Clarification applied", item));
    }

    @PostMapping("/api/test-cases/{id}/accept")
    public Mono<ApiResponse<TestCaseResponse>> accept(@PathVariable UUID id) {
        long started = System.nanoTime();
        TraceLogger.info("CONTROLLER", "ENTER", "accept", TraceMeta.of("testCaseId", id.toString()));
        return intentService.accept(id)
                .doOnSuccess(item -> TraceLogger.info("CONTROLLER", "EXIT", "accept",
                        (System.nanoTime() - started) / 1_000_000,
                        TraceMeta.of("status", "SUCCESS")))
                .doOnError(error -> TraceLogger.error("CONTROLLER", "EXIT", "accept failed", error,
                        (System.nanoTime() - started) / 1_000_000,
                        TraceMeta.of("status", "FAILED")))
                .map(item -> ApiResponse.ok("Intent accepted", item));
    }
}
