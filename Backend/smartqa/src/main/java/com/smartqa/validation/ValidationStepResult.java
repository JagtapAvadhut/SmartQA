package com.smartqa.validation;

public record ValidationStepResult(
        int stepNumber,
        String action,
        String target,
        String status,
        String errorMessage,
        long durationMs
) {
}
