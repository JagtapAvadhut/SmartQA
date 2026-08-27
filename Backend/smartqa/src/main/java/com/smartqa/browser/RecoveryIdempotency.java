package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.smartqa.pipeline.FailureEvidence;

import java.util.Locale;
import java.util.Map;

/**
 * Skips side-effecting recovery when the intended state is already present.
 */
public final class RecoveryIdempotency {

    private RecoveryIdempotency() {
    }

    public static boolean alreadySatisfied(Page page, String recoveryType, FailureEvidence evidence) {
        if (page == null || recoveryType == null) {
            return false;
        }
        String type = recoveryType.trim().toUpperCase(Locale.ROOT);
        try {
            return switch (type) {
                case "RETRY_STEP", "RE_NAVIGATE" -> looksSubmitted(page, evidence);
                case "RE_APPLY_FILTER" -> filterAlreadyApplied(page, evidence);
                case "RESELECT_AUTOCOMPLETE", "RESEARCH_SEARCH_RESULT" -> searchAlreadyLanded(page, evidence);
                default -> false;
            };
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean looksSubmitted(Page page, FailureEvidence evidence) {
        Object raw = page.evaluate("""
                () => {
                  const text = ((document.body && document.body.innerText) || '').toLowerCase();
                  const cart = (document.body && document.body.innerText || '').match(/\\b(\\d+)\\s*(item|items)?\\b/i);
                  const formGone = !document.querySelector('form input[type="submit"], button[type="submit"]')
                    || !document.querySelector('input[type="password"]');
                  const success = /\\b(success|saved|created|added to cart|order placed)\\b/.test(text);
                  return { success, formGone };
                }
                """);
        if (raw instanceof Map<?, ?> map) {
            return Boolean.TRUE.equals(map.get("success")) || Boolean.TRUE.equals(map.get("formGone"));
        }
        String expected = evidence == null ? "" : safe(evidence.expected()).toLowerCase(Locale.ROOT);
        if (expected.isBlank()) {
            return false;
        }
        try {
            String body = page.locator("body").innerText();
            return body != null && body.toLowerCase(Locale.ROOT).contains(expected);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean filterAlreadyApplied(Page page, FailureEvidence evidence) {
        String expected = evidence == null ? "" : firstNonBlank(evidence.expected(), evidence.actual());
        if (expected == null || expected.isBlank()) {
            return false;
        }
        Object raw = page.evaluate("""
                (needle) => {
                  const n = String(needle || '').toLowerCase();
                  if (!n) return false;
                  const checked = Array.from(document.querySelectorAll(
                    'input[type="checkbox"]:checked, input[type="radio"]:checked, [aria-checked="true"]'
                  ));
                  return checked.some(el => {
                    const label = ((el.labels && el.labels[0] && el.labels[0].innerText)
                      || el.getAttribute('aria-label') || el.value || el.innerText || '').toLowerCase();
                    return label.includes(n);
                  });
                }
                """, expected);
        return Boolean.TRUE.equals(raw);
    }

    private static boolean searchAlreadyLanded(Page page, FailureEvidence evidence) {
        String expected = evidence == null ? "" : firstNonBlank(evidence.expected(), evidence.actual());
        if (expected == null || expected.isBlank()) {
            return false;
        }
        try {
            String body = page.locator("body").innerText();
            String url = page.url() == null ? "" : page.url().toLowerCase(Locale.ROOT);
            String needle = expected.toLowerCase(Locale.ROOT);
            return (body != null && body.toLowerCase(Locale.ROOT).contains(needle))
                    || url.contains(needle.replace(" ", "+"))
                    || url.contains(needle.replace(" ", "%20"));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
