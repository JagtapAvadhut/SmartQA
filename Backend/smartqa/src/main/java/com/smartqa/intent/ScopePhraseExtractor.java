package com.smartqa.intent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts container/scope phrases that must constrain resolution.
 * Words such as inside/within/under/below are semantic constraints, not decoration.
 */
public final class ScopePhraseExtractor {

    private static final Pattern SCOPE = Pattern.compile(
            "(?i)\\b(?:inside|insite|within|under|below|in)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:section|panel|area|form|modal|dialog|dialog box|cart|filter|menu))?$");
    private static final Pattern SCOPE_PREFIX = Pattern.compile(
            "(?i)^(?:inside|insite|within|under|below|in)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:section|panel|area|form|modal|dialog|cart|filter|menu))?[,:]?\\s+");

    private ScopePhraseExtractor() {
    }

    public static String extract(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Matcher prefix = SCOPE_PREFIX.matcher(line.trim());
        if (prefix.find()) {
            return clean(prefix.group(1));
        }
        Matcher whole = SCOPE.matcher(line.trim());
        if (whole.matches()) {
            return clean(whole.group(1));
        }
        return null;
    }

    public static String stripScopePrefix(String line) {
        if (line == null) {
            return null;
        }
        return SCOPE_PREFIX.matcher(line.trim()).replaceFirst("");
    }

    public static boolean isScopeWord(String token) {
        if (token == null) {
            return false;
        }
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "inside", "insite", "within", "under", "below", "in" -> true;
            default -> false;
        };
    }

    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("(?i)\\b(the|section|panel|area)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
