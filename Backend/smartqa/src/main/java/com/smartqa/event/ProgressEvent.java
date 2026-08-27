package com.smartqa.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProgressEvent(
        String type,
        String message,
        UUID testCaseId,
        UUID executionRunId,
        Instant timestamp,
        Map<String, Object> details,
        Integer stepNumber,
        Integer totalSteps,
        String currentUrl,
        String pageTitle,
        String executionProvider,
        String screenshotId,
        Long eventId
) {
    public static ProgressEvent generation(String type, String message, UUID testCaseId) {
        return new ProgressEvent(type, message, testCaseId, null, Instant.now(), Map.of(),
                null, null, null, null, null, null, null);
    }

    public static ProgressEvent generation(String type, String message, UUID testCaseId, Map<String, Object> details) {
        return new ProgressEvent(type, message, testCaseId, null, Instant.now(), details == null ? Map.of() : details,
                null, null, null, null, null, null, null);
    }

    public static ProgressEvent execution(String type, String message, UUID testCaseId, UUID runId) {
        return new ProgressEvent(type, message, testCaseId, runId, Instant.now(), Map.of(),
                null, null, null, null, null, null, null);
    }

    public static ProgressEvent execution(
            String type, String message, UUID testCaseId, UUID runId, Map<String, Object> details) {
        return new ProgressEvent(type, message, testCaseId, runId, Instant.now(), details == null ? Map.of() : details,
                null, null, null, null, null, null, null);
    }

    public static ProgressEvent rich(String type, String message, UUID testCaseId, UUID runId,
                                     Map<String, Object> details, Integer stepNumber, Integer totalSteps,
                                     String currentUrl, String pageTitle, String executionProvider,
                                     String screenshotId) {
        return new ProgressEvent(type, message, testCaseId, runId, Instant.now(),
                details == null ? Map.of() : details,
                stepNumber, totalSteps, currentUrl, pageTitle, executionProvider, screenshotId, null);
    }

    public ProgressEvent withEventId(long eventId) {
        return new ProgressEvent(type, message, testCaseId, executionRunId, timestamp, details,
                stepNumber, totalSteps, currentUrl, pageTitle, executionProvider, screenshotId, eventId);
    }

    public ProgressEvent withoutEventId() {
        return new ProgressEvent(type, message, testCaseId, executionRunId, timestamp, details,
                stepNumber, totalSteps, currentUrl, pageTitle, executionProvider, screenshotId, null);
    }

    public ProgressEvent withDetails(Map<String, Object> details) {
        return new ProgressEvent(type, message, testCaseId, executionRunId, timestamp,
                details == null ? Map.of() : details,
                stepNumber, totalSteps, currentUrl, pageTitle, executionProvider, screenshotId, eventId);
    }
}
