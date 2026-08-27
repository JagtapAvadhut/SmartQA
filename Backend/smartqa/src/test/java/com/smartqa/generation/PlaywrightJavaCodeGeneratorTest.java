package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;
import com.smartqa.intent.IntentContract;
import com.smartqa.intent.IntentScenario;
import com.smartqa.intent.IntentStep;
import com.smartqa.testcase.TestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaywrightJavaCodeGeneratorTest {

    @Test
    void promptIncludesTestNameStepsAndLocatorMemory() {
        TestCase testCase = new TestCase();
        testCase.setId(UUID.randomUUID());
        testCase.setName("Example IANA");
        testCase.setNaturalLanguage("Open example.com\nClick More information");
        IntentContract intent = new IntentContract(
                "READY",
                "Example IANA",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "navigate", "https://example.com", null, null),
                        new IntentStep("s1_step2", "click", "More information", null, null)
                ))),
                List.of()
        );
        LocatorMemoryDocument memory = new LocatorMemoryDocument(List.of(
                new LocatorMemoryEntry(
                        "s1_step2",
                        "click",
                        "More information",
                        "More information",
                        "text",
                        0.9,
                        "More information",
                        null,
                        "https://example.com",
                        false,
                        null,
                        null
                )
        ));

        String prompt = PlaywrightJavaCodeGenerator.buildUserPrompt(testCase, intent, memory, "ExampleIanaTest");
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("Example IANA"));
        assertTrue(prompt.contains("More information"));
        assertTrue(prompt.contains("locator=More information"));
        assertTrue(prompt.contains("Class name: ExampleIanaTest"));
        assertTrue(prompt.contains("Do not invent selectors"));
        assertTrue(prompt.contains("Use only verified locator memory"));
    }
}
