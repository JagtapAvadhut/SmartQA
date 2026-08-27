package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;

/**
 * Generic native &lt;select&gt; matching: exact value/label, then numeric-normalized label (₹40000 vs 40000).
 */
public final class NativeSelectHandler {

    private NativeSelectHandler() {
    }

    public static void selectOption(Locator select, String wanted) {
        if (select == null || wanted == null || wanted.isBlank()) {
            return;
        }
        try {
            select.selectOption(wanted);
            return;
        } catch (RuntimeException first) {
            String match = findMatchingOption(select, wanted);
            if (match != null) {
                TraceLogger.info("FILTER", "NATIVE_SELECT_FUZZY_MATCH", "Matched select option by normalized label",
                        TraceMeta.of("wanted", wanted, "matched", match));
                select.selectOption(match);
                return;
            }
            throw first;
        }
    }

    public static void selectOption(Page page, Locator select, String wanted) {
        try {
            selectOption(select, wanted);
        } catch (RuntimeException nativeFailed) {
            if (page != null) {
                TraceLogger.warn("PLAYWRIGHT", "NATIVE_SELECT_FALLBACK_CUSTOM",
                        "Native select matching failed; trying custom dropdown",
                        TraceMeta.of("error", nativeFailed.getMessage() == null ? "" : nativeFailed.getMessage()));
                CustomDropdownHandler.selectOption(page, select, wanted);
                return;
            }
            throw nativeFailed;
        }
    }

    static String findMatchingOption(Locator select, String wanted) {
        try {
            Locator options = select.locator("option");
            int count = Math.min(options.count(), 80);
            String wantNorm = normalize(wanted);
            String wantDigits = digits(wanted);
            long wantCompact = compactNumber(wanted);
            TraceLogger.info("FILTER", "NATIVE_SELECT_OPTIONS_INSPECTED", "Inspected native select options",
                    TraceMeta.of("wanted", wanted, "optionCount", count, "wantDigits", wantDigits));
            String best = null;
            for (int i = 0; i < count; i++) {
                Locator option = options.nth(i);
                String label = safeText(option);
                String value = safeAttr(option, "value");
                if (matches(wantNorm, wantDigits, wantCompact, label)
                        || matches(wantNorm, wantDigits, wantCompact, value)) {
                    return value != null && !value.isBlank() ? value : label;
                }
                if (best == null && wantCompact > 0 && compactNumber(label) == wantCompact) {
                    best = value != null && !value.isBlank() ? value : label;
                }
            }
            return best;
        } catch (RuntimeException ex) {
            TraceLogger.warn("FILTER", "NATIVE_SELECT_INSPECT_FAILED", "Native select option inspection failed",
                    TraceMeta.of("error", ex.getMessage() == null ? "" : ex.getMessage()));
            return null;
        }
    }

    /**
     * Comparison strategy: exact normalized text, then full digit equality, then compact numeric equality.
     * Numeric substring matching is intentionally rejected so "40" does not match 40000.
     */
    static boolean sameLogicalValue(String wanted, String candidate) {
        if (wanted == null || candidate == null) {
            return false;
        }
        return matches(normalize(wanted), digits(wanted), compactNumber(wanted), candidate);
    }

    private static boolean matches(String wantNorm, String wantDigits, long wantCompact, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String norm = normalize(candidate);
        if (norm.equals(wantNorm)) {
            return true;
        }
        boolean wantNumeric = wantCompact > 0 || !wantDigits.isEmpty();
        if (!wantNumeric && wantNorm.length() >= 2 && (norm.contains(wantNorm) || wantNorm.contains(norm))) {
            return true;
        }
        String candDigits = digits(candidate);
        if (!wantDigits.isEmpty() && wantDigits.equals(candDigits)) {
            return true;
        }
        long candCompact = compactNumber(candidate);
        return wantCompact > 0 && candCompact == wantCompact;
    }

    /**
     * Generic compact numeric parse: 40000, ₹40,000, 40,000, ₹40K → 40000.
     */
    static long compactNumber(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        String s = value.trim().toLowerCase(Locale.ROOT).replace("₹", "").replace("$", "").replace(",", "").replaceAll("\\s+", "");
        double multiplier = 1;
        if (s.endsWith("k")) {
            multiplier = 1000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            multiplier = 1_000_000;
            s = s.substring(0, s.length() - 1);
        }
        s = s.replaceAll("[^0-9.]", "");
        if (s.isBlank()) {
            return -1;
        }
        try {
            return Math.round(Double.parseDouble(s) * multiplier);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static String safeText(Locator locator) {
        try {
            String text = locator.innerText();
            return text == null ? "" : text.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeAttr(Locator locator, String name) {
        try {
            String value = locator.getAttribute(name);
            return value == null ? "" : value;
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
