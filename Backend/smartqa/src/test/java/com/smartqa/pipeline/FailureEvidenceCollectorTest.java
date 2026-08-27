package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureEvidenceCollectorTest {

    private final FailureEvidenceCollector collector = new FailureEvidenceCollector();

    @Test
    void enrichesHostMismatchOnSiblingSubdomain() {
        FailureEvidence base = FailureEvidence.builder()
                .url("https://export.indiamart.com/")
                .expected("Double screen mobile phone near Mumbai")
                .actual("missing heading")
                .failureCategory("ASSERTION")
                .previousAttempts(List.of())
                .build();
        FailureEvidence enriched = collector.enrichWithHostMismatch(base, "https://www.indiamart.com/");
        assertTrue(enriched.actual().contains("host_mismatch") || enriched.actual().contains("export.indiamart.com"));
        assertEquals("Double screen mobile phone near Mumbai", enriched.expected());
    }

    @Test
    void sameRegistrableDomainHelper() {
        assertTrue(FailureEvidenceCollector.sameRegistrableDomain("www.indiamart.com", "export.indiamart.com"));
        assertEquals("export.indiamart.com", FailureEvidenceCollector.hostOf("https://export.indiamart.com/foo"));
    }

    @Test
    void aiContextDoesNotInventEqualsOnlyLocatorWhenMissing() {
        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://www.urbancompany.com/pune")
                .actual("Actionability failed: element does not exist")
                .failureCategory("ACTIONABILITY")
                .build();
        String ctx = evidence.toAiContext();
        assertTrue(ctx.contains("Locator: (missing"));
        assertFalse(ctx.contains("Locator: =\n") || ctx.endsWith("Locator: ="));
    }
}
