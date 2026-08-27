package com.smartqa.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeSanitizerTest {

    @Test
    void stripsPasswordsTokensAndPii() {
        String raw = "password=admin123 api_key=sk-abcdefghijklmnop otp=123456 "
                + "Bearer eyJhbGciOiJIUzI1NiJ9.aaa.bbb contact me@example.com +91 9876543210 "
                + "Employee Radha Gupta";
        String sanitized = KnowledgeSanitizer.sanitize(raw);
        assertFalse(sanitized.toLowerCase().contains("admin123"));
        assertFalse(sanitized.contains("sk-abcdefghijklmnop"));
        assertFalse(sanitized.contains("me@example.com"));
        assertFalse(sanitized.contains("Radha"));
        assertTrue(sanitized.contains("[REDACTED"));
    }

    @Test
    void looksSecretHeavyRejectsCredentialBlobs() {
        assertTrue(KnowledgeSanitizer.looksSecretHeavy("password=admin123"));
        assertTrue(KnowledgeSanitizer.looksSecretHeavy("api_key=xyz"));
        assertFalse(KnowledgeSanitizer.looksSecretHeavy(
                "Filters may be implemented as accordion + checkbox + chip."));
    }

    @Test
    void vectorLiteralRoundTrip() {
        float[] v = new float[]{0.1f, -0.2f, 0.3f};
        String lit = VectorLiteral.toLiteral(v);
        assertEquals("[0.1,-0.2,0.3]", lit);
        float[] back = VectorLiteral.fromLiteral(lit);
        assertEquals(3, back.length);
        assertEquals(0.1f, back[0], 0.0001f);
    }

    @Test
    void cosineSimilarityOfIdenticalVectorsIsOne() {
        float[] a = new float[]{1f, 0f, 0f};
        assertEquals(1.0, VectorLiteral.cosineSimilarity(a, a), 0.0001);
    }
}
