package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageNormalizerTest {

    @Test
    void correctsHarmlessTyposButKeepsQuotedBusinessText() {
        String normalized = LanguageNormalizer.normalize("insite Brand selct \"AK-1\" chekbox");
        assertTrue(normalized.toLowerCase().contains("inside"));
        assertTrue(normalized.toLowerCase().contains("select"));
        assertTrue(normalized.contains("\"AK-1\""));
        assertEquals("inside Brand select \"AK-1\" checkbox", LanguageNormalizer.normalize("insite Brand selct \"AK-1\" chekbox"));
    }
}
