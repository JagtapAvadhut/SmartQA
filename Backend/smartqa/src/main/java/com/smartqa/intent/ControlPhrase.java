package com.smartqa.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generic control-word detection for intent phrases. No website-specific rules.
 * Explicit words such as "checkbox" outrank verbs such as "select".
 */
public final class ControlPhrase {

    public static final String CHECKBOX = "checkbox";
    public static final String RADIO = "radio";
    public static final String DROPDOWN = "dropdown";
    public static final String INPUT = "input";
    public static final String BUTTON = "button";
    public static final String LINK = "link";
    public static final String VISUAL = "visual";

    private static final Pattern CHECKBOX_WORD = Pattern.compile(
            "(?i)\\b(check\\s*boxes?|check\\s*box|tick\\s*boxes?|tick\\s*box|checkboxes?|"
                    + "untick|uncheck|tick|check)\\b");
    private static final Pattern EXPLICIT_CHECKBOX = Pattern.compile(
            "(?i)\\b(check\\s*boxes?|check\\s*box|tick\\s*boxes?|tick\\s*box|checkboxes?|checkbox)\\b");
    private static final Pattern RADIO_WORD = Pattern.compile("(?i)\\b(radio\\s*buttons?|radios?)\\b");
    private static final Pattern DROPDOWN_WORD = Pattern.compile(
            "(?i)\\b(drop\\s*downs?|dropdowns?|combo\\s*boxes?|comboboxes?|from\\s+dropdown)\\b");
    private static final Pattern INPUT_WORD = Pattern.compile(
            "(?i)\\b(text\\s*boxes?|textboxes?|text\\s*fields?|input\\s*fields?|input\\s*boxes?)\\b");
    private static final Pattern BUTTON_WORD = Pattern.compile("(?i)\\bbuttons?\\b");
    private static final Pattern LINK_WORD = Pattern.compile("(?i)\\blinks?\\b");
    private static final Pattern VISUAL_WORD = Pattern.compile(
            "(?i)\\b(image|img|photo|picture|icon|banner|canvas)\\b");

    private static final Pattern CONTROL_STRIP = Pattern.compile(
            "(?i)\\b(check\\s*boxes?|check\\s*box|tick\\s*boxes?|tick\\s*box|checkboxes?|checkbox|"
                    + "radio\\s*buttons?|radios?|drop\\s*downs?|dropdowns?|combo\\s*boxes?|comboboxes?|"
                    + "text\\s*boxes?|textboxes?|text\\s*fields?|input\\s*fields?|input\\s*boxes?|"
                    + "buttons?|links?|option|the|a|an)\\b");

    private static final Pattern FILLER_VERB = Pattern.compile(
            "(?i)\\b(select|choose|pick|apply|filter|click|tap|open|tick|check|uncheck|untick)\\b");

    static final Set<String> FILTER_FIELD_HINTS = Set.of(
            "brand", "state", "city", "color", "size", "price", "rating", "category",
            "gender", "type", "occasion", "fit", "storage", "ram", "processor",
            "model", "availability", "customer rating", "discount", "material", "pattern",
            "os", "operating system", "connectivity");

    private static final Set<String> GENERIC_TARGETS = Set.of(
            "dropdown", "drop down", "combobox", "combo box", "select", "select box",
            "checkbox", "radio", "option", "control", "element");

    private static final Set<String> FILLER_TOKENS = Set.of(
            "near", "in", "inside", "within", "under", "from", "with", "into", "onto",
            "the", "this", "that", "your", "please", "just", "then", "also", "using",
            "via", "all", "search", "box", "field");

    private ControlPhrase() {
    }

    public static String detect(String... parts) {
        String blob = join(parts);
        if (blob.isBlank()) {
            return "";
        }
        // Explicit widget words win over the verb "select".
        if (EXPLICIT_CHECKBOX.matcher(blob).find()
                || (CHECKBOX_WORD.matcher(blob).find() && !DROPDOWN_WORD.matcher(blob).find())) {
            if (EXPLICIT_CHECKBOX.matcher(blob).find()
                    || blob.toLowerCase(Locale.ROOT).contains("tick")
                    || blob.toLowerCase(Locale.ROOT).contains("uncheck")
                    || blob.toLowerCase(Locale.ROOT).contains("untick")) {
                return CHECKBOX;
            }
        }
        if (RADIO_WORD.matcher(blob).find()) {
            return RADIO;
        }
        if (DROPDOWN_WORD.matcher(blob).find()) {
            return DROPDOWN;
        }
        if (INPUT_WORD.matcher(blob).find()) {
            return INPUT;
        }
        if (VISUAL_WORD.matcher(blob).find()
                && (blob.toLowerCase(Locale.ROOT).contains("containing")
                || blob.toLowerCase(Locale.ROOT).contains("shows")
                || blob.toLowerCase(Locale.ROOT).contains("says")
                || blob.toLowerCase(Locale.ROOT).contains("with text"))) {
            return VISUAL;
        }
        if (BUTTON_WORD.matcher(blob).find()) {
            return BUTTON;
        }
        if (LINK_WORD.matcher(blob).find()) {
            return LINK;
        }
        if (EXPLICIT_CHECKBOX.matcher(blob).find() || CHECKBOX_WORD.matcher(blob).find()) {
            return CHECKBOX;
        }
        return "";
    }

    public static boolean hasCheckbox(String... parts) {
        return CHECKBOX.equals(detect(parts));
    }

    public static boolean hasRadio(String... parts) {
        return RADIO.equals(detect(parts));
    }

    public static boolean hasDropdown(String... parts) {
        return DROPDOWN.equals(detect(parts));
    }

    public static boolean isGenericTarget(String target) {
        if (target == null || target.isBlank()) {
            return true;
        }
        return GENERIC_TARGETS.contains(target.trim().toLowerCase(Locale.ROOT));
    }

    public static String stripControlWords(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = CONTROL_STRIP.matcher(text).replaceAll(" ");
        cleaned = FILLER_VERB.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("[=:]+", " ").replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    public static boolean isFilterFieldToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (FILTER_FIELD_HINTS.contains(t)) {
            return true;
        }
        if (t.endsWith("s") && t.length() > 2) {
            return FILTER_FIELD_HINTS.contains(t.substring(0, t.length() - 1));
        }
        return false;
    }

    public static String singularFilterField(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String original = token.trim();
        String t = original.toLowerCase(Locale.ROOT);
        if (FILTER_FIELD_HINTS.contains(t)) {
            return original;
        }
        if (t.endsWith("s") && t.length() > 2) {
            String singular = t.substring(0, t.length() - 1);
            if (FILTER_FIELD_HINTS.contains(singular)) {
                return singular;
            }
        }
        return original;
    }

    public static String firstFilterFieldToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String token : tokens(text)) {
            if (isFilterFieldToken(token)) {
                return singularFilterField(token);
            }
        }
        return null;
    }

    public static boolean looksLikeAllFieldHeading(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.trim();
        if (t.equalsIgnoreCase("all")) {
            return true;
        }
        return isFilterFieldToken(t)
                && t.equals(t.toUpperCase(Locale.ROOT))
                && t.length() > 3;
    }

    public static String unwrapQuotes(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text.trim();
        if (t.length() >= 2) {
            char start = t.charAt(0);
            if (start == '\'' || start == '"') {
                int end = t.indexOf(start, 1);
                if (end > 1) {
                    return t.substring(1, end).trim();
                }
            }
        }
        return t;
    }

    public static String stripVerifyPrefix(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String t = text.trim();
        t = t.replaceFirst("(?i)^(?:that\\s+)?(?:the\\s+)?text\\s+as\\s+", "");
        t = t.replaceFirst("(?i)^(?:that\\s+)?(?:the\\s+)?text\\s+is\\s+", "");
        return unwrapQuotes(t);
    }

    public static boolean looksLikeOptionCode(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.trim();
        if (t.length() > 12) {
            return false;
        }
        boolean letters = false;
        boolean digits = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isLetter(ch)) {
                letters = true;
            } else if (Character.isDigit(ch)) {
                digits = true;
            } else {
                return false;
            }
        }
        if (t.equals(t.toUpperCase(Locale.ROOT)) && t.length() >= 2 && t.length() <= 8) {
            return true;
        }
        return letters && digits && t.length() <= 10;
    }

    /**
     * Filler words and generic widget names — not search queries such as "volvo".
     */
    public static boolean isJoinerToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.trim();
        return "&".equals(t) || "/".equals(t) || "-".equals(t) || "and".equalsIgnoreCase(t);
    }

    /**
     * Multi-word control names such as "Brand & Model" or "Price Range" are atomic
     * targets for expand/click. They are not field/value pairs.
     */
    public static boolean looksLikeCompoundControlName(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = stripControlWords(text);
        if (t.isBlank()) {
            t = text.trim();
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains(" & ") || lower.contains(" / ") || lower.contains(" - ")) {
            return true;
        }
        String[] words = t.split("\\s+");
        if (words.length < 2 || words.length > 4) {
            return false;
        }
        String last = words[words.length - 1];
        if (looksLikeOptionCode(last)) {
            return false;
        }
        int titled = 0;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (Character.isUpperCase(word.charAt(0)) || isFilterFieldToken(word)) {
                titled++;
            }
        }
        return titled >= 2;
    }

    public static boolean looksLikeNoiseToken(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String t = token.trim();
        if (isJoinerToken(t)) {
            return false;
        }
        if (isFilterFieldToken(t) || looksLikeOptionCode(t)) {
            return false;
        }
        if (t.length() <= 1) {
            return true;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (GENERIC_TARGETS.contains(lower) || FILLER_TOKENS.contains(lower)) {
            return true;
        }
        return false;
    }

    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String part : text.trim().split("\\s+")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    private static String join(String... parts) {
        if (parts == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }
}
