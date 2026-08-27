package com.smartqa.execution.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionEvent(
        String traceId,
        UUID executionRunId,
        UUID testCaseId,
        String stepId,
        int stepNumber,
        Instant timestamp,
        EventLevel level,
        EventComponent component,
        EventType eventType,
        String message,
        String currentUrl,
        String pageTitle,
        String action,
        String target,
        String locator,
        String controlType,
        double confidence,
        String resolvedTag,
        String resolvedRole,
        String expectedControlType,
        String status,
        long durationMs,
        String screenshotId,
        String errorCode,
        String errorMessage,
        String executionProvider,
        Map<String, Object> metadata
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String traceId;
        private UUID executionRunId;
        private UUID testCaseId;
        private String stepId;
        private int stepNumber;
        private EventLevel level = EventLevel.INFO;
        private EventComponent component = EventComponent.EXECUTION;
        private EventType eventType;
        private String message;
        private String currentUrl;
        private String pageTitle;
        private String action;
        private String target;
        private String locator;
        private String controlType;
        private double confidence;
        private String resolvedTag;
        private String resolvedRole;
        private String expectedControlType;
        private String status;
        private long durationMs;
        private String screenshotId;
        private String errorCode;
        private String errorMessage;
        private String executionProvider;
        private Map<String, Object> metadata = Map.of();

        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder executionRunId(UUID v) { this.executionRunId = v; return this; }
        public Builder testCaseId(UUID v) { this.testCaseId = v; return this; }
        public Builder stepId(String v) { this.stepId = v; return this; }
        public Builder stepNumber(int v) { this.stepNumber = v; return this; }
        public Builder level(EventLevel v) { this.level = v; return this; }
        public Builder component(EventComponent v) { this.component = v; return this; }
        public Builder eventType(EventType v) { this.eventType = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder currentUrl(String v) { this.currentUrl = v; return this; }
        public Builder pageTitle(String v) { this.pageTitle = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder target(String v) { this.target = v; return this; }
        public Builder locator(String v) { this.locator = v; return this; }
        public Builder controlType(String v) { this.controlType = v; return this; }
        public Builder confidence(double v) { this.confidence = v; return this; }
        public Builder resolvedTag(String v) { this.resolvedTag = v; return this; }
        public Builder resolvedRole(String v) { this.resolvedRole = v; return this; }
        public Builder expectedControlType(String v) { this.expectedControlType = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder durationMs(long v) { this.durationMs = v; return this; }
        public Builder screenshotId(String v) { this.screenshotId = v; return this; }
        public Builder errorCode(String v) { this.errorCode = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public Builder executionProvider(String v) { this.executionProvider = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v == null ? Map.of() : v; return this; }

        public ExecutionEvent build() {
            return new ExecutionEvent(
                    traceId, executionRunId, testCaseId, stepId, stepNumber,
                    Instant.now(), level, component, eventType, message,
                    currentUrl, pageTitle, action, target, locator,
                    controlType, confidence, resolvedTag, resolvedRole,
                    expectedControlType, status, durationMs, screenshotId,
                    errorCode, errorMessage, executionProvider, metadata
            );
        }
    }
}
