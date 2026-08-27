package com.smartqa.debug;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMaskerTest {

    @SuppressWarnings("unchecked")
    @Test
    void masksPasswordFields() {
        Map<String, Object> masked = (Map<String, Object>) SecretMasker.mask(Map.of(
                "applicationUrl", "https://example.com",
                "password", "admin123",
                "apiKey", "secret-key"
        ));
        assertEquals("https://example.com", masked.get("applicationUrl"));
        assertEquals(SecretMasker.MASK, masked.get("password"));
        assertEquals(SecretMasker.MASK, masked.get("apiKey"));
    }

    @Test
    void masksInlinePasswordAssignments() {
        String masked = SecretMasker.maskText("type password=admin123 in the form");
        assertTrue(masked.contains("password=" + SecretMasker.MASK));
        assertFalse(masked.contains("admin123"));
    }

    @Test
    void masksBearerTokens() {
        String masked = SecretMasker.maskText("Authorization: Bearer abc.def.ghi");
        assertTrue(masked.contains(SecretMasker.MASK));
        assertFalse(masked.contains("abc.def.ghi"));
    }

    @Test
    void masksEmailPhoneJwtAndCard() {
        String masked = SecretMasker.maskText(
                "user jane@example.com phone +1 555-123-4567 jwt eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb card 4111 1111 1111 1111");
        assertFalse(masked.contains("jane@example.com"));
        assertFalse(masked.contains("555-123-4567"));
        assertFalse(masked.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
        assertFalse(masked.contains("4111 1111 1111 1111"));
        assertTrue(masked.contains(SecretMasker.MASK));
    }
}
