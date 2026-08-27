package com.smartqa.browser.intelligence.memory;

import java.time.Instant;

public record ExecutionMemoryRecord(
        MemoryScope scope,
        String applicationHost,
        String testCaseId,
        String executionId,
        String action,
        String semanticTarget,
        String role,
        String parentContext,
        String frameContext,
        String shadowContext,
        String locatorType,
        String locatorHint,
        double confidence,
        boolean success,
        Instant at,
        String controlType,
        String container,
        String verifiedLocatorStrategy,
        String evidenceMomentId
) {
    public ExecutionMemoryRecord(
            MemoryScope scope,
            String applicationHost,
            String testCaseId,
            String executionId,
            String action,
            String semanticTarget,
            String role,
            String parentContext,
            String frameContext,
            String shadowContext,
            String locatorType,
            String locatorHint,
            double confidence,
            boolean success,
            Instant at
    ) {
        this(scope, applicationHost, testCaseId, executionId, action, semanticTarget, role, parentContext,
                frameContext, shadowContext, locatorType, locatorHint, confidence, success, at,
                null, parentContext, locatorType, null);
    }
}
