package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionFailureAnalyzerTest {

    private final AssertionFailureAnalyzer analyzer = new AssertionFailureAnalyzer();

    @Test
    void keepsPasswordMismatchAssertionAuthoritative() {
        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://opensource-demo.orangehrmlive.com/web/index.php/pim/addEmployee")
                .expected("Passwords do not match")
                .actual("text not visible")
                .visibleTextExcerpt("Add Employee Employee Full Name Employee Id Password Confirm Password Required")
                .exception("Assertion failed: expected text \"Passwords do not match\" was not found on page")
                .failureCategory("ASSERTION")
                .build();

        AiDiagnosticResult result = analyzer.analyze(evidence, "ASSERTION");
        assertNotNull(result);
        assertEquals("ASSERTION", result.normalizedClassification());
        assertEquals("ASSERTION_NOT_REACHED", result.assertionSubCategory());
        assertTrue(result.explanation().contains("Passwords do not match"));
        assertFalse(Boolean.TRUE.equals(result.requiresSourceFix()));
        assertTrue(result.recoveryOptions().stream().anyMatch(o -> "VERIFY_ASSERTION_CONTEXT".equals(o.type())));
    }

    @Test
    void competingPasswordErrorIsBusinessMismatch() {
        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://example.com/login")
                .expected("Passwords do not match")
                .actual("Invalid credentials")
                .visibleTextExcerpt("Login Username Password Invalid credentials")
                .exception("Assertion failed: expected text \"Passwords do not match\" was not found on page")
                .failureCategory("ASSERTION")
                .build();

        AiDiagnosticResult result = analyzer.analyze(evidence, "ASSERTION");
        assertNotNull(result);
        assertEquals("BUSINESS_STATE_MISMATCH", result.normalizedClassification());
        assertFalse(Boolean.TRUE.equals(result.requiresSourceFix()));
    }

    @Test
    void classifiesWrongHostAssertionSeparately() {
        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://export.indiamart.com/")
                .expected("Double screen mobile phone near Mumbai")
                .exception("Assertion failed ... wrong_host=https://export.indiamart.com/")
                .visibleTextExcerpt("Export IndiaMART")
                .failureCategory("ASSERTION")
                .build();

        AiDiagnosticResult result = analyzer.analyze(evidence, "ASSERTION");
        assertNotNull(result);
        assertEquals("WRONG_HOST", result.normalizedClassification());
        assertEquals("ASSERTION_WRONG_PAGE", result.assertionSubCategory());
    }
}
