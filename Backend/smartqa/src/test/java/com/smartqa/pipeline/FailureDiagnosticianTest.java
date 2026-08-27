package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureDiagnosticianTest {

    private final FailureDiagnostician diagnostician = new FailureDiagnostician();

    @Test
    void classifiesCompoundControlSplitAsIntentNormalization() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "GENERATE",
                "semanticField=Brand value=& Model controlType=checkbox for Brand & Model expand",
                1,
                3);
        assertEquals("INTENT_NORMALIZATION_FAILURE", diagnosis.category());
        assertEquals("Intent Compiler", diagnosis.responsibleComponent());
        assertFalse(diagnosis.category().equals("VALIDATOR_FAILURE"));
    }

    @Test
    void retriesSearchStateMismatch() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "GENERATE",
                "SEARCH_STATE_MISMATCH: requested 'Smartphones' but page state was homepage chrome",
                1,
                3);
        assertEquals("SEARCH_RESOLUTION", diagnosis.category());
        assertTrue(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
        FailureDiagnosis specialized = FailureDiagnosis.of(
                diagnosis.whatFailed(),
                diagnosis.rootCause(),
                diagnosis.responsibleComponent(),
                "SEARCH_STATE_MISMATCH",
                diagnosis.evidence(),
                true,
                1,
                diagnosis.recommendedAction());
        assertTrue(diagnostician.shouldAutoRetry(specialized, 1, 3));
    }

    @Test
    void classifiesFilterFailuresAsHealable() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "EXECUTE",
                "Double Screen Mobile Phone filter not applied in Related Categories",
                1,
                3);
        assertEquals("FILTER_TARGET_RESOLUTION", diagnosis.category());
        assertEquals("Filter Intelligence Engine", diagnosis.responsibleComponent());
        assertTrue(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }

    @Test
    void doesNotAutoRetryBareAssertionSemantics() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "VALIDATE",
                "Assertion failed: expected Passwords do not match",
                1,
                3);
        assertEquals("ASSERTION_FAILURE", diagnosis.category());
        assertFalse(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }

    @Test
    void classifiesExportHostAsWrongHost() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "EXECUTE",
                "Assertion failed: expected text \"Double screen mobile phone near Mumbai\" was not found on page https://export.indiamart.com/ | wrong_host=https://export.indiamart.com/",
                1,
                3);
        assertEquals("WRONG_PAGE_STATE", diagnosis.category());
        assertTrue(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }

    @Test
    void retriesAssertionWhenAiSaysWrongHost() {
        FailureDiagnosis base = diagnostician.diagnose(
                "EXECUTE",
                "Assertion failed: expected text \"Double screen mobile phone near Mumbai\" was not found",
                1,
                3);
        AiDiagnosticResult ai = AiDiagnosticResult.fallback("WRONG_HOST", "WRONG_RESULT_HOST", "Host diverged", 0.94);
        ai.setAssertionSubCategory("ASSERTION_WRONG_PAGE");
        FailureDiagnosis enriched = base.withEnrichment(null, ai, List.of("RESTORE_EXPECTED_HOST"), null, true, false, null);
        assertTrue(diagnostician.shouldAutoRetry(enriched, 1, 3));
    }

    @Test
    void stopsRetryAfterMaxAttempts() {
        FailureDiagnosis diagnosis = diagnostician.diagnose("GENERATE", "element not found", 3, 3);
        assertEquals("TARGET_NOT_FOUND", diagnosis.category());
        assertFalse(diagnostician.shouldAutoRetry(diagnosis, 3, 3));
    }

    @Test
    void classifiesActionabilityElementDoesNotExist() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "GENERATE",
                "Actionability failed: element does not exist",
                1,
                3);
        assertEquals("ACTIONABILITY_FAILURE", diagnosis.category());
        assertTrue(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }

    @Test
    void classifiesLocatorInvalidContractFailure() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "GENERATE",
                "LOCATOR_INVALID: malformed selector equals-only payload | type=css | value= | source=ElementResolver",
                1,
                3);
        assertEquals("TARGET_NOT_FOUND", diagnosis.category());
        assertTrue(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }

    @Test
    void multipleMatchingFilterOptionIsNotUserInstruction() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "EXECUTE",
                "Multiple matching elements for 'AK': Explore Plus, Login, Become a Seller, Cart, Flights",
                1,
                3);
        assertEquals("FILTER_TARGET_RESOLUTION", diagnosis.category());
        assertTrue(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }

    @Test
    void locatorLikeUnderstandFailureIsIntentValidationAndNotRetried() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "UNDERSTAND",
                "Intent step s1_step1 contains locator-like implementation details. Provide semantic instructions only.",
                1,
                3);
        assertEquals("INTENT_VALIDATION", diagnosis.category());
        assertFalse(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }

    @Test
    void clarificationRequiresUserInput() {
        FailureDiagnosis diagnosis = diagnostician.diagnose(
                "UNDERSTAND",
                "Ambiguous: which profile icon?",
                1,
                3);
        assertEquals("USER_INSTRUCTION", diagnosis.category());
        assertFalse(diagnostician.shouldAutoRetry(diagnosis, 1, 3));
    }
}
