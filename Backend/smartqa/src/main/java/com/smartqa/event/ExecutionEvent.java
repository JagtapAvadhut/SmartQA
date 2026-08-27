package com.smartqa.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical structured execution/pipeline event. ProgressEvent remains the SSE payload;
 * this model is the logging contract for correlation.
 */
public record ExecutionEvent(
        String eventId,
        String traceId,
        UUID pipelineRunId,
        UUID executionRunId,
        UUID testCaseId,
        Integer stepNumber,
        String eventType,
        String level,
        String component,
        String operation,
        String message,
        String action,
        String target,
        String locator,
        Double confidence,
        String url,
        String pageTitle,
        String screenshotId,
        String errorCode,
        Instant timestamp,
        Long durationMs,
        Map<String, Object> details
) {
    public static ExecutionEvent fromProgress(ProgressEvent event, UUID pipelineRunId) {
        Map<String, Object> details = event.details() == null ? Map.of() : event.details();
        String level = switch (event.type() == null ? "" : event.type()) {
            case "PIPELINE_FAILED", "GENERATION_ERROR", "EXECUTION_FAILED", "ASSERTION_FAILED", "ACTION_FAILED" -> "ERROR";
            case "PIPELINE_STOPPED", "PIPELINE_BLOCKED" -> "WARN";
            default -> "INFO";
        };
        Object shot = details.get("screenshotId");
        String screenshotId = event.screenshotId() != null
                ? event.screenshotId()
                : (shot == null ? null : String.valueOf(shot));
        Object err = details.get("errorCode");
        return new ExecutionEvent(
                event.eventId() == null ? null : String.valueOf(event.eventId()),
                string(details.get("traceId")),
                pipelineRunId,
                event.executionRunId(),
                event.testCaseId(),
                event.stepNumber(),
                event.type(),
                level,
                "PIPELINE",
                event.type(),
                event.message(),
                string(details.get("action")),
                string(details.get("target")),
                string(details.get("locator")),
                number(details.get("confidence")),
                event.currentUrl(),
                event.pageTitle(),
                screenshotId,
                err == null ? null : String.valueOf(err),
                event.timestamp() == null ? Instant.now() : event.timestamp(),
                null,
                details
        );
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double number(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }
}
