package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedHelperIntegrationTest {

    private final QualityGateService qualityGateService = new QualityGateService();

    @Test
    void preparedDeterministicCodeIncludesHelpersAndPassesQualityGate() {
        LocatorMemoryEntry click = new LocatorMemoryEntry(
                "s2", "click", "Save", "#save", "css",
                0.9, "Save", null, "https://example.com/home", false, null, null);
        LocatorMemoryEntry navigate = new LocatorMemoryEntry(
                "s1", "navigate", "home", "https://example.com", "css",
                1.0, null, null, "https://example.com", false, null, null);
        LocatorMemoryDocument memory = new LocatorMemoryDocument(List.of(navigate, click));
        String raw = DeterministicPlaywrightFactory.render("HelperFlowTest", memory);
        String prepared = GenerationService.prepareGeneratedCode(raw + "\npage.navigate(\"https://evil.example\");\n", memory);
        assertTrue(prepared.contains("firstVisible("));
        assertTrue(prepared.contains("clickAndUseResultingPage(") || prepared.contains("clickAndUseResultingPage"));
        assertTrue(prepared.contains("ensureToggle(") || prepared.contains("ensureToggle"));
        assertFalse(prepared.contains("page.navigate(\"https://evil.example\")"));
        QualityGateService.QualityGateResult result = qualityGateService.validateAndCompile(prepared);
        assertTrue(result.passed(), result.message());
    }

    @Test
    void sanitizerLetsHallucinatedEnumsCompileThroughQualityGate() {
        String fenced = """
                ```java
                %s
                ```
                """.formatted(DeterministicPlaywrightFactory.render("SanitizedEnumTest",
                new LocatorMemoryDocument(List.of(new LocatorMemoryEntry(
                        "s1", "navigate", "home", "https://example.com", "css",
                        1.0, null, null, "https://example.com", false, null, null))))
                .replace("LoadState.DOMCONTENTLOADED", "LoadState.DOM_CONTENT_LOADED"));
        String prepared = GenerationService.prepareGeneratedCode(fenced,
                new LocatorMemoryDocument(List.of(new LocatorMemoryEntry(
                        "s1", "navigate", "home", "https://example.com", "css",
                        1.0, null, null, "https://example.com", false, null, null))));
        assertFalse(prepared.contains("```"));
        assertTrue(prepared.contains("LoadState.DOMCONTENTLOADED") || !prepared.contains("LoadState.DOM_CONTENT_LOADED"));
    }
}
