package com.smartqa.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VerifyExpectation {

    private static final Set<String> OPERATORS = Set.of(
            "contains",
            "equals",
            "equal",
            "eq",
            "visible",
            "exists",
            "present",
            "true",
            "text",
            "heading",
            "not_equals",
            "greater_than",
            "less_than",
            "between",
            "in"
    );

    private VerifyExpectation() {
    }

    public static String expectedText(String assertion, String value) {
        if (isOperator(assertion)) {
            return blankToNull(stripVerifyDecorators(value));
        }
        if (value != null && !value.isBlank()) {
            return stripVerifyDecorators(value);
        }
        if (assertion != null && !assertion.isBlank() && !isOperator(assertion)) {
            return stripVerifyDecorators(assertion);
        }
        return null;
    }

    public static boolean isOperator(String assertion) {
        if (assertion == null || assertion.isBlank()) {
            return false;
        }
        return OPERATORS.contains(assertion.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isPageLevelTarget(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        String hint = target.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return hint.equals("page")
                || hint.equals("body")
                || hint.equals("content")
                || hint.equals("text")
                || hint.equals("document")
                || hint.equals("screen")
                || hint.equals("page content")
                || hint.equals("page text")
                || hint.equals("body text")
                || hint.contains("page content");
    }

    public static boolean isTitleTarget(String target) {
        return target != null && target.toLowerCase(Locale.ROOT).contains("title");
    }

    public enum RecordOutcome {
        LITERAL,
        PRESENT,
        ABSENT
    }

    /**
     * Business-outcome phrases such as "matching employee record exists" are not UI copy.
     * Classify them so verification can look at result rows / "records found" instead of
     * requiring the instruction text itself to appear on the page.
     */
    public static RecordOutcome recordOutcome(String expected) {
        if (expected == null || expected.isBlank()) {
            return RecordOutcome.LITERAL;
        }
        String n = expected.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (n.equals("record found") || n.equals("records found") || n.equals("no records found")
                || n.equals("no record found")) {
            return RecordOutcome.LITERAL;
        }
        boolean absent = n.contains("no record")
                || n.contains("no matching")
                || n.contains("no result")
                || n.contains("zero record");
        if (absent) {
            return RecordOutcome.ABSENT;
        }
        if ((n.contains("matching") && n.contains("record"))
                || n.contains("record exists")
                || n.contains("records exist")
                || n.contains("employee record")
                || n.equals("records found")
                || n.equals("record found")) {
            return RecordOutcome.PRESENT;
        }
        return RecordOutcome.LITERAL;
    }

    /**
     * Visible copy often pluralizes: "Record Found" vs "Records Found".
     * Keep the original expected string; also try the obvious English plural/singular.
     */
    public static List<String> textVariants(String expected) {
        if (expected == null || expected.isBlank()) {
            return List.of();
        }
        String trim = expected.trim();
        List<String> variants = new ArrayList<>();
        variants.add(trim);
        String lower = trim.toLowerCase(Locale.ROOT);
        if (lower.equals("record found")) {
            variants.add("Records Found");
        } else if (lower.equals("records found")) {
            variants.add("Record Found");
        } else if (lower.equals("no record found")) {
            variants.add("No Records Found");
        } else if (lower.equals("no records found")) {
            variants.add("No Record Found");
        }
        return variants;
    }

    public static boolean isSpecificExpectedText(String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String lower = expected.toLowerCase(Locale.ROOT).trim();
        return !isOperator(lower)
                && !lower.contains("visible")
                && !lower.contains("expected")
                && !lower.equals("true")
                && !lower.equals("heading");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stripVerifyDecorators(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String t = text.trim();
        t = t.replaceFirst("(?i)^(?:that\\s+)?(?:the\\s+)?text\\s+as\\s+", "");
        t = t.replaceFirst("(?i)^(?:that\\s+)?(?:the\\s+)?text\\s+is\\s+", "");
        t = t.trim();
        if (t.length() >= 2) {
            char start = t.charAt(0);
            char end = t.charAt(t.length() - 1);
            if ((start == '\'' && end == '\'') || (start == '"' && end == '"')) {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }
}
