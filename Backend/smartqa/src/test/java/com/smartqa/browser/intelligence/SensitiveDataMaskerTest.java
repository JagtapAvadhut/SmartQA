package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataMaskerTest {

    @Test
    void masksTokenQueryAndLeavesPath() {
        String masked = SensitiveDataMasker.maskUrl("https://api.example/search?q=ak&token=super-secret");
        assertTrue(masked.contains("q=ak"));
        assertTrue(masked.contains("token=***"));
        assertFalse(masked.contains("super-secret"));
    }

    @Test
    void flagsSensitiveHeaders() {
        assertTrue(SensitiveDataMasker.sensitiveHeader("Authorization"));
        assertTrue(SensitiveDataMasker.sensitiveHeader("Cookie"));
        assertFalse(SensitiveDataMasker.sensitiveHeader("Content-Type"));
    }
}
