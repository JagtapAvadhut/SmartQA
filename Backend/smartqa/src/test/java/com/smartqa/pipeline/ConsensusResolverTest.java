package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsensusResolverTest {

    @Test
    void agreementBoostsConfidence() {
        AiDiagnosticResult gemini = diagnosis("AMBIGUOUS_ELEMENT", "RE_RESOLVE", 0.8);
        AiDiagnosticResult ollama = diagnosis("AMBIGUOUS_TARGET", "REDISCOVER_ELEMENT", 0.75);

        ConsensusResolver.ConsensusOutcome outcome = ConsensusResolver.resolve(
                gemini, "gemini", ollama, "ollama");

        assertTrue(outcome.agreed());
        assertFalse(outcome.requiresDeterministicReinspect());
        assertTrue(outcome.merged().confidence() > 0.8);
        assertEquals("agreement_boosted", outcome.reason());
    }

    @Test
    void disagreementForcesReinspectWithoutBlindExecute() {
        AiDiagnosticResult gemini = diagnosis("WRONG_HOST", "RESTORE_EXPECTED_HOST", 0.9);
        AiDiagnosticResult ollama = diagnosis("ASSERTION", "VERIFY_ASSERTION_CONTEXT", 0.7);

        ConsensusResolver.ConsensusOutcome outcome = ConsensusResolver.resolve(
                gemini, "gemini", ollama, "ollama");

        assertFalse(outcome.agreed());
        assertTrue(outcome.requiresDeterministicReinspect());
        assertTrue(outcome.merged().confidence() <= ConsensusResolver.DEFAULT_LOW_CONFIDENCE);
        assertTrue(outcome.reason().startsWith("disagreement_"));
    }

    @Test
    void businessAmbiguityDisagreementAsksUser() {
        AiDiagnosticResult gemini = diagnosis("AMBIGUOUS_ELEMENT", "RE_RESOLVE", 0.7);
        gemini.setRequiresUserInput(false);
        AiDiagnosticResult ollama = diagnosis("APPLICATION", "RETRY_STEP", 0.6);

        ConsensusResolver.ConsensusOutcome outcome = ConsensusResolver.resolve(
                gemini, "gemini", ollama, "ollama");

        assertFalse(outcome.agreed());
        assertTrue(outcome.requiresUserInput());
        assertTrue(outcome.merged().requiresUserInput());
    }

    @Test
    void secondaryMissingUsesPrimary() {
        AiDiagnosticResult gemini = diagnosis("FILTER_NOT_OPEN", "OPEN_CONTROL", 0.88);
        ConsensusResolver.ConsensusOutcome outcome = ConsensusResolver.resolve(
                gemini, "gemini", null, "ollama");
        assertFalse(outcome.agreed());
        assertEquals("FILTER_NOT_OPEN", outcome.merged().normalizedClassification());
        assertEquals("secondary_unavailable", outcome.reason());
    }

    private static AiDiagnosticResult diagnosis(String classification, String strategy, double confidence) {
        AiDiagnosticResult result = new AiDiagnosticResult();
        result.setClassification(classification);
        result.setConfidence(confidence);
        result.setExplanation("test");
        RecoveryOption option = new RecoveryOption(strategy, "safe", true);
        option.setConfidence(confidence);
        result.setRecoveryOptions(List.of(option));
        return result;
    }
}
