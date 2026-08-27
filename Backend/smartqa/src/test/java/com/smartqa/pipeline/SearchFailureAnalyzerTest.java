package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFailureAnalyzerTest {

    private final SearchFailureAnalyzer analyzer = new SearchFailureAnalyzer();

    @Test
    void diagnosesExportHostAsWrongHostWithoutWeakeningAssertion() {
        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://export.indiamart.com/")
                .expected("Double screen mobile phone near Mumbai")
                .actual("host=export.indiamart.com")
                .exception("Assertion failed: expected text \"Double screen mobile phone near Mumbai\" was not found on page https://export.indiamart.com/")
                .failureCategory("ASSERTION")
                .action("verify")
                .build();

        AiDiagnosticResult result = analyzer.analyze(evidence);
        assertNotNull(result);
        assertEquals("WRONG_HOST", result.normalizedClassification());
        assertEquals("WRONG_RESULT_HOST", result.rootCause());
        assertTrue(result.explanation().contains("domestic"));
        assertFalse(result.explanation().toLowerCase().contains("change the expected"));
        assertTrue(result.recoveryOptions().stream().anyMatch(o -> "RESTORE_EXPECTED_HOST".equals(o.type())));
        assertEquals("Double screen mobile phone near Mumbai", evidence.expected());
    }
}
