package com.smartqa.validation;

import java.util.List;

public record ValidationResult(
        String status,
        List<ValidationStepResult> steps,
        int totalSteps,
        int passedSteps,
        int failedSteps,
        Integer failedStepNumber,
        String failedAction,
        String errorMessage,
        String screenshotId,
        long durationMs,
        int attemptNumber
) {
    public static ValidationResult pass(List<ValidationStepResult> steps, long durationMs, int attemptNumber) {
        return new ValidationResult("PASSED", steps, steps.size(), steps.size(), 0,
                null, null, null, null, durationMs, attemptNumber);
    }

    public static ValidationResult fail(List<ValidationStepResult> steps, int failedStep, String failedAction,
                                        String error, String screenshotId, long durationMs, int attemptNumber) {
        int passed = 0;
        for (ValidationStepResult s : steps) {
            if ("PASSED".equals(s.status())) passed++;
        }
        return new ValidationResult("FAILED", steps, steps.size(), passed, steps.size() - passed,
                failedStep, failedAction, error, screenshotId, durationMs, attemptNumber);
    }
}
