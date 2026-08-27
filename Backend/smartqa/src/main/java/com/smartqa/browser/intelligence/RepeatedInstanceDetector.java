package com.smartqa.browser.intelligence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects repeated cards/rows/items from structural signatures, not website IDs.
 * Used for "Edit for John", "first product", "checkbox in row 4".
 */
public final class RepeatedInstanceDetector {

    private static final Pattern ORDINAL = Pattern.compile("(?i)\\b(?:(\\d+)(?:st|nd|rd|th)|first|second|third|last)\\b");
    private static final Pattern ROW = Pattern.compile("(?i)\\brow\\s+(\\d+)\\b");

    private RepeatedInstanceDetector() {
    }

    public static String signature(ElementCandidate element) {
        if (element == null) {
            return "";
        }
        ElementStructure structure = element.structureOrEmpty();
        String parent = firstNonBlank(element.parentTag(), parentToken(element.parentContext()));
        String role = firstNonBlank(element.role(), element.actionableRole(), structure.axRole());
        String tag = firstNonBlank(element.tag(), element.actionableTag());
        String region = firstNonBlank(element.region(), element.headingContext());
        int childCount = structure.childIds() == null || structure.childIds().isBlank()
                ? 0
                : structure.childIds().split(",").length;
        return String.join("|",
                tag.toLowerCase(Locale.ROOT),
                role.toLowerCase(Locale.ROOT),
                parent.toLowerCase(Locale.ROOT),
                String.valueOf(childCount),
                region.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim());
    }

    public static Map<String, List<ElementCandidate>> group(List<ElementCandidate> elements) {
        Map<String, List<ElementCandidate>> groups = new LinkedHashMap<>();
        if (elements == null) {
            return groups;
        }
        for (ElementCandidate element : elements) {
            String key = signature(element);
            if (key.isBlank()) {
                continue;
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(element);
        }
        return groups;
    }

    public static boolean repeated(List<ElementCandidate> elements) {
        for (List<ElementCandidate> group : group(elements).values()) {
            if (group.size() >= 2) {
                return true;
            }
        }
        return false;
    }

    public static Integer requestedIndex(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        Matcher row = ROW.matcher(instruction);
        if (row.find()) {
            return Integer.parseInt(row.group(1));
        }
        Matcher ordinal = ORDINAL.matcher(instruction);
        if (!ordinal.find()) {
            return null;
        }
        String token = ordinal.group().toLowerCase(Locale.ROOT);
        if (token.contains("first")) {
            return 1;
        }
        if (token.contains("second")) {
            return 2;
        }
        if (token.contains("third")) {
            return 3;
        }
        if (token.contains("last")) {
            return -1;
        }
        String digits = token.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        return Integer.parseInt(digits);
    }

    private static String parentToken(String parentContext) {
        if (parentContext == null || parentContext.isBlank()) {
            return "";
        }
        String[] parts = parentContext.trim().split("\\s+");
        return parts.length == 0 ? "" : parts[0];
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
