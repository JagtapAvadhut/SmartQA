package com.smartqa.intent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic structured filter parsing. No website-specific rules.
 * "Brand HP" → field=Brand, operator=equals, value=HP
 * "Price 40000 to 60000" → field=Price, operator=between, min/max.
 */
public final class FilterIntentParser {

    private static final Pattern BETWEEN_AND = Pattern.compile(
            "(?i)^(.+?)\\s+between\\s+([0-9,.]+)\\s+and\\s+([0-9,.]+)$");
    private static final Pattern FROM_TO = Pattern.compile(
            "(?i)^(.+?)\\s+(?:from\\s+)?([0-9,.]+)\\+?\\s+(?:to|-|and)\\s+([0-9,.]+)\\+?$");

    private FilterIntentParser() {
    }

    public static IntentFilter parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = ControlPhrase.stripControlWords(raw.trim());
        if (trimmed.isBlank()) {
            trimmed = raw.trim();
        }
        Matcher between = BETWEEN_AND.matcher(trimmed);
        if (between.matches()) {
            return range(between.group(1), between.group(2), between.group(3));
        }
        Matcher to = FROM_TO.matcher(trimmed);
        if (to.matches() && looksLikeField(to.group(1))) {
            return range(to.group(1), to.group(2), to.group(3));
        }
        Matcher minMax = Pattern.compile(
                "(?i)^(.+?)\\s+(?:min(?:imum)?|from)\\s+([0-9,.]+)\\+?\\s+(?:max(?:imum)?|to|upto|and)\\s+([0-9,.]+)\\+?$")
                .matcher(trimmed);
        if (minMax.matches() && looksLikeField(minMax.group(1))) {
            return range(minMax.group(1), minMax.group(2), minMax.group(3));
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            return new IntentFilter(
                    trimmed.substring(0, colon).trim(),
                    "equals",
                    trimmed.substring(colon + 1).trim(),
                    null,
                    null);
        }
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length == 2 && !isCompoundRemainder(parts[0], parts[1])) {
            return new IntentFilter(parts[0], "equals", parts[1], null, null);
        }
        return new IntentFilter(trimmed, "equals", null, null, null);
    }

    private static IntentFilter range(String field, String minRaw, String maxRaw) {
        return new IntentFilter(
                field == null ? null : field.trim(),
                "between",
                null,
                parseNumber(minRaw),
                parseNumber(maxRaw));
    }

    private static boolean isCompoundRemainder(String field, String rest) {
        if (rest == null || rest.isBlank()) {
            return false;
        }
        String r = rest.trim();
        if (r.startsWith("&") || r.startsWith("/") || r.startsWith("-")) {
            return true;
        }
        String first = r.split("\\s+")[0];
        return ControlPhrase.isFilterFieldToken(first) && !ControlPhrase.looksLikeOptionCode(first);
    }

    private static boolean looksLikeField(String field) {
        if (field == null || field.isBlank()) {
            return false;
        }
        String[] words = field.trim().split("\\s+");
        return words.length <= 3 && field.length() <= 40;
    }

    private static Double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", "").replace("+", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "equals";
        }
        String lower = operator.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("from") || lower.equals("range") || lower.equals("to")) {
            return "between";
        }
        return lower;
    }
}
