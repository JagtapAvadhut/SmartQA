package com.smartqa.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocatorHealingResolverTest {

    @Test
    void parsesLocatorCloudTokens() {
        List<LocatorHealingResolver.Alternative> parsed = LocatorHealingResolver.parseCloud(
                "css:#save@0.9 | text:Save@0.8 | role:button|Save@0.7");
        assertEquals(3, parsed.size());
        assertEquals("css", parsed.get(0).type());
        assertEquals("#save", parsed.get(0).value());
        assertEquals(0.9, parsed.get(0).confidence());
        assertEquals("text", parsed.get(1).type());
        assertEquals("role", parsed.get(2).type());
        assertEquals("button|Save", parsed.get(2).value());
    }
}
