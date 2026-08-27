package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Compacts verified locator memory before it is sent to a codegen prompt.
 * Keeps the winning locator plus a bounded alternative cloud.
 */
public final class LocatorMemoryPromptCompactor {

    public static final int MAX_CLOUD_ALTERNATIVES = 4;
    public static final int MAX_ERROR_CHARS = 180;

    private LocatorMemoryPromptCompactor() {
    }

    public static LocatorMemoryDocument compact(LocatorMemoryDocument memory) {
        if (memory == null || memory.entries() == null) {
            return new LocatorMemoryDocument(List.of());
        }
        List<LocatorMemoryEntry> compacted = new ArrayList<>();
        for (LocatorMemoryEntry entry : memory.entries()) {
            compacted.add(compactEntry(entry));
        }
        return new LocatorMemoryDocument(List.copyOf(compacted));
    }

    public static String compactCloud(String locatorCloud) {
        if (locatorCloud == null || locatorCloud.isBlank()) {
            return "";
        }
        String[] parts = locatorCloud.split("\\|");
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.length() > MAX_ERROR_CHARS) {
                token = token.substring(0, MAX_ERROR_CHARS);
            }
            kept.add(token);
            if (kept.size() >= MAX_CLOUD_ALTERNATIVES) {
                break;
            }
        }
        return String.join(" | ", kept);
    }

    private static LocatorMemoryEntry compactEntry(LocatorMemoryEntry entry) {
        if (entry == null) {
            return null;
        }
        return new LocatorMemoryEntry(
                entry.stepId(),
                entry.action(),
                entry.semanticTarget(),
                entry.resolvedLocator(),
                entry.locatorType(),
                entry.confidence(),
                truncate(entry.elementText(), MAX_ERROR_CHARS),
                null,
                entry.pageUrl(),
                entry.healed(),
                entry.value(),
                null,
                compactCloud(entry.locatorCloud()),
                entry.controlType(),
                entry.frameContext(),
                entry.frameUrl(),
                entry.frameName(),
                entry.parentFrameContext(),
                entry.targetPath()
        );
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
