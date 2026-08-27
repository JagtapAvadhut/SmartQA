package com.smartqa.testcase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TestCaseResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        String status,
        String naturalLanguage,
        String generatedCode,
        String locatorMemory,
        String intentContract,
        List<ScenarioResponse> scenarios,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record ScenarioResponse(UUID id, String name, int order, List<StepResponse> steps) {
    }

    public record StepResponse(UUID id, int order, String text) {
    }
}
