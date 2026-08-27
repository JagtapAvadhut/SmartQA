package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryPlanValidatorTest {

    @Test
    void acceptsSafeRestoreHost() {
        RecoveryOption option = new RecoveryOption("RESTORE_EXPECTED_HOST", "Return to domestic host", true);
        option.setConfidence(0.9);
        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://export.indiamart.com/")
                .build();
        RecoveryPlanValidator.ValidationResult result = RecoveryPlanValidator.validateOne(
                option, evidence, "https://www.indiamart.com/", 0.55);
        assertTrue(result.accepted());
    }

    @Test
    void rejectsArbitraryScript() {
        RecoveryOption option = new RecoveryOption("REDISCOVER", "run javascript:alert(1)", true);
        option.setConfidence(0.9);
        RecoveryPlanValidator.ValidationResult result = RecoveryPlanValidator.validateOne(
                option, null, "https://www.indiamart.com/", 0.55);
        assertFalse(result.accepted());
    }

    @Test
    void rejectsUnsupportedType() {
        RecoveryOption option = new RecoveryOption("RUN_ARBITRARY_XPATH", "click //div", true);
        option.setConfidence(0.9);
        RecoveryPlanValidator.ValidationResult result = RecoveryPlanValidator.validateOne(
                option, null, "https://example.com", 0.55);
        assertFalse(result.accepted());
    }

    @Test
    void rejectsLowConfidenceBatch() {
        AiDiagnosticResult ai = AiDiagnosticResult.fallback("SEARCH", "X", "y", 0.2);
        ai.setRecoveryOptions(List.of(new RecoveryOption("REFRESH_DOM", "retry", true)));
        List<RecoveryPlanValidator.ValidationResult> results = RecoveryPlanValidator.validateAll(
                ai, null, "https://example.com", 0.55);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().noneMatch(RecoveryPlanValidator.ValidationResult::accepted));
    }
}
