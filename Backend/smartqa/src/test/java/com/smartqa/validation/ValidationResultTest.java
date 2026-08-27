package com.smartqa.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {

    @Test
    void passResult() {
        List<ValidationStepResult> steps = List.of(
                new ValidationStepResult(1, "navigate", "url", "PASSED", null, 100),
                new ValidationStepResult(2, "click", "Login", "PASSED", null, 200)
        );
        ValidationResult result = ValidationResult.pass(steps, 300, 1);
        assertEquals("PASSED", result.status());
        assertEquals(2, result.totalSteps());
        assertEquals(2, result.passedSteps());
        assertEquals(0, result.failedSteps());
        assertNull(result.failedStepNumber());
        assertEquals(300, result.durationMs());
        assertEquals(1, result.attemptNumber());
    }

    @Test
    void failResult() {
        List<ValidationStepResult> steps = List.of(
                new ValidationStepResult(1, "navigate", "url", "PASSED", null, 100),
                new ValidationStepResult(2, "click", "Login", "FAILED", "Element not found", 200)
        );
        ValidationResult result = ValidationResult.fail(steps, 2, "click", "Element not found", null, 300, 1);
        assertEquals("FAILED", result.status());
        assertEquals(2, result.totalSteps());
        assertEquals(1, result.passedSteps());
        assertEquals(1, result.failedSteps());
        assertEquals(2, result.failedStepNumber());
        assertEquals("click", result.failedAction());
        assertEquals("Element not found", result.errorMessage());
    }

    @Test
    void validationIsIndependentFromQualityGate() {
        ValidationResult failed = ValidationResult.fail(
                List.of(new ValidationStepResult(1, "test", "target", "FAILED", "assertion error", 0)),
                1, "test", "assertion error", null, 100, 1);
        assertEquals("FAILED", failed.status());
    }
}
