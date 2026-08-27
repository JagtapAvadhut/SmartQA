package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCodeNavigationContractTest {

    @Test
    void removesUnrecordedNavigateCalls() {
        LocatorMemoryEntry navigate = new LocatorMemoryEntry(
                "s1", "navigate", "home", "https://app.example.com/login", "css",
                1.0, null, null, "https://app.example.com/login", false, null, null);
        String source = """
                page.navigate("https://app.example.com/login");
                page.locator("#save").click();
                page.navigate("https://evil.example.com/exfil");
                """;
        String cleaned = GeneratedCodeNavigationContract.stripUnrecordedNavigations(
                source, new LocatorMemoryDocument(List.of(navigate)));
        assertTrue(cleaned.contains("https://app.example.com/login"));
        assertFalse(cleaned.contains("page.navigate(\"https://evil.example.com/exfil\")"));
        assertTrue(cleaned.contains("unrecorded navigation removed"));
    }
}
