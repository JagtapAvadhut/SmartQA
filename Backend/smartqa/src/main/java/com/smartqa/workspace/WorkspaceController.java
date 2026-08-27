package com.smartqa.workspace;

import com.smartqa.ai.AiCalls;
import com.smartqa.common.api.ApiResponse;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@RestController
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final SmartQaProperties properties;

    public WorkspaceController(WorkspaceService workspaceService, SmartQaProperties properties) {
        this.workspaceService = workspaceService;
        this.properties = properties;
    }

    @PostMapping("/api/workspace/analyze")
    public Mono<ApiResponse<WorkspaceAnalyzeResponse>> analyze(@Valid @RequestBody WorkspaceAnalyzeRequest request) {
        return Mono.deferContextual(ctx -> {
            String traceId = TraceContext.from(ctx);
            TraceContext.set(traceId);
            long started = System.nanoTime();
            int instructionLength = request == null || request.instructions() == null ? 0 : request.instructions().length();
            int timeoutSeconds = analyzeTimeoutSeconds();
            TraceLogger.info("CONTROLLER", "ENTER", "workspaceAnalyze", TraceMeta.of(
                    "operation", "ANALYZE",
                    "instructionLength", instructionLength,
                    "timeoutSeconds", timeoutSeconds,
                    "traceId", traceId
            ));
            return workspaceService.analyze(request)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorMap(TimeoutException.class, error -> new SmartQaException(
                            ErrorCode.AI_TIMEOUT,
                            "Analyze did not complete within " + timeoutSeconds
                                    + " seconds (traceId=" + traceId + "). Check AI provider responsiveness.",
                            error))
                    .doOnSuccess(ignored -> TraceLogger.info("CONTROLLER", "CONTROLLER_EXIT", "workspaceAnalyze",
                            (System.nanoTime() - started) / 1_000_000,
                            TraceMeta.of("status", "SUCCESS", "traceId", traceId)))
                    .doOnError(error -> TraceLogger.error("CONTROLLER", "CONTROLLER_EXIT", "workspaceAnalyze failed", error,
                            (System.nanoTime() - started) / 1_000_000,
                            TraceMeta.of("status", "FAILED", "traceId", traceId,
                                    "errorCategory", error instanceof SmartQaException sqe ? sqe.errorCode().name() : error.getClass().getSimpleName())))
                    .map(item -> ApiResponse.ok("Intent analyzed", item));
        });
    }

    private int analyzeTimeoutSeconds() {
        int intent = AiCalls.intentTimeoutSeconds(properties);
        int execution = properties.getExecution().getTimeoutSeconds();
        int overall = Math.min(intent * 2 + 15, Math.max(30, execution - 20));
        return Math.max(30, Math.min(120, overall));
    }
}
