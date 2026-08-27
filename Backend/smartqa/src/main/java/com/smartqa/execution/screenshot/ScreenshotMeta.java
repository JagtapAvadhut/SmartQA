package com.smartqa.execution.screenshot;

import java.time.Instant;
import java.util.UUID;

public record ScreenshotMeta(
        String id,
        String traceId,
        UUID executionRunId,
        String stepId,
        int stepNumber,
        String eventType,
        Instant timestamp,
        String url,
        String filePath,
        String evidenceMomentId
) {
    public ScreenshotMeta(
            String id,
            String traceId,
            UUID executionRunId,
            String stepId,
            int stepNumber,
            String eventType,
            Instant timestamp,
            String url,
            String filePath
    ) {
        this(id, traceId, executionRunId, stepId, stepNumber, eventType, timestamp, url, filePath, null);
    }
}
