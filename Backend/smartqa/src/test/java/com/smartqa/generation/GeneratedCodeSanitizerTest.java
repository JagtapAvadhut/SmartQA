package com.smartqa.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCodeSanitizerTest {

    @Test
    void stripsMarkdownFenceAndFixesHallucinatedEnums() {
        String source = """
                ```java
                page.waitForLoadState(LoadState.DOM_CONTENT_LOADED);
                page.getByRole(AriaRole.TEXT_BOX).fill("x");
                ```
                """;
        String sanitized = GeneratedCodeSanitizer.sanitize(source);
        assertFalse(sanitized.contains("```"));
        assertTrue(sanitized.contains("LoadState.DOMCONTENTLOADED"));
        assertTrue(sanitized.contains("AriaRole.TEXTBOX"));
        assertFalse(sanitized.contains("LoadState.DOM_CONTENT_LOADED"));
    }

    @Test
    void leavesAlreadyValidSourceUnchanged() {
        String source = "page.getByRole(AriaRole.BUTTON).click();";
        assertEquals(source, GeneratedCodeSanitizer.sanitize(source));
    }
}
