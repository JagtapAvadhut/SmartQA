package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorMemoryPromptCompactorTest {

    @Test
    void boundsLocatorCloudAlternatives() {
        String cloud = "css:#a@0.9 | css:#b@0.8 | css:#c@0.7 | css:#d@0.6 | css:#e@0.5 | css:#f@0.4";
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "click", "Save", "#save", "css",
                0.9, "Save", "attrs-too-large", "https://app.example.com", false, null, "/tmp/x.png",
                cloud, "BUTTON");
        LocatorMemoryDocument compacted = LocatorMemoryPromptCompactor.compact(
                new LocatorMemoryDocument(List.of(entry)));
        String compactCloud = compacted.entries().getFirst().locatorCloud();
        assertEquals(4, compactCloud.split("\\|").length);
        assertTrue(compactCloud.contains("css:#a@0.9"));
        assertTrue(compacted.entries().getFirst().screenshotPath() == null
                || compacted.entries().getFirst().screenshotPath().isBlank());
    }
}
