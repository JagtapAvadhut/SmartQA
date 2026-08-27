package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;

/**
 * Generic autocomplete/typeahead confirmation after filling a hint field.
 */
public final class AutocompleteHandler {

    private AutocompleteHandler() {
    }

    public static void confirmSelectionIfNeeded(Page page, Locator input, String value) {
        confirmSelectionIfNeeded(page, input, value, null);
    }

    /**
     * @param expectedApplicationUrl when set, prefer suggestions that stay on the requested host family
     *                                and restore host if selection redirects to a sibling subdomain.
     */
    public static void confirmSelectionIfNeeded(Page page, Locator input, String value, String expectedApplicationUrl) {
        if (page == null || value == null || value.isBlank() || !needsConfirmation(input)) {
            return;
        }
        TraceLogger.info("AUTocomplete", "AUTOCOMPLETE_CONFIRM_STARTED", "Confirming autocomplete selection", TraceMeta.of(
                "value", value
        ));
        try {
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(value))
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(8000));
        } catch (RuntimeException ignored) {
            try {
                page.locator("[role='listbox'] [role='option'], [role='listbox'] > *, [role='option']")
                        .first()
                        .waitFor(new Locator.WaitForOptions().setTimeout(5000));
            } catch (RuntimeException ignoredToo) {
            }
        }
        Locator option = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(value));
        if (selectVisible(option) && !isExportLikeCandidate(option.first())) {
            SafeClick.click(option.first(), page);
            afterSelect(page, expectedApplicationUrl, value, "role-option");
            return;
        }
        Locator listbox = page.locator("[role='listbox'] [role='option'], [role='listbox'] > *");
        Locator fallbackExport = null;
        for (int i = 0; i < listbox.count(); i++) {
            Locator candidate = listbox.nth(i);
            if (!candidate.isVisible()) {
                continue;
            }
            if (isExportLikeCandidate(candidate)) {
                if (fallbackExport == null && (textContains(candidate, value)
                        || SearchStateContract.containsDistinctiveTokens(value, safeText(candidate)))) {
                    fallbackExport = candidate;
                }
                continue;
            }
            if (SearchStateContract.conflicts(value, safeText(candidate))) {
                continue;
            }
            if (textContains(candidate, value)) {
                SafeClick.click(candidate, page);
                afterSelect(page, expectedApplicationUrl, value, "listbox-item");
                return;
            }
            if (SearchStateContract.containsDistinctiveTokens(value, safeText(candidate))) {
                SafeClick.click(candidate, page);
                afterSelect(page, expectedApplicationUrl, value, "listbox-token");
                return;
            }
        }
        Locator textMatch = page.getByText(value, new Page.GetByTextOptions().setExact(true));
        for (int i = 0; i < textMatch.count(); i++) {
            Locator candidate = textMatch.nth(i);
            if (!candidate.isVisible() || isSameNode(input, candidate) || isExportLikeCandidate(candidate)) {
                continue;
            }
            String tag = safeTag(candidate);
            if ("input".equals(tag) || "textarea".equals(tag)) {
                continue;
            }
            SafeClick.click(candidate, page);
            afterSelect(page, expectedApplicationUrl, value, "text-match");
            return;
        }
        Locator suggestions = page.locator(
                "[class*='autocomplete'] [class*='option'], [class*='suggestion'], [class*='typeahead'] li, [class*='typeahead'] [class*='item']");
        for (int i = 0; i < suggestions.count(); i++) {
            Locator candidate = suggestions.nth(i);
            if (!candidate.isVisible() || isExportLikeCandidate(candidate)) {
                continue;
            }
            if (textContains(candidate, value) || SearchStateContract.containsDistinctiveTokens(value, safeText(candidate))) {
                SafeClick.click(candidate, page);
                afterSelect(page, expectedApplicationUrl, value, "suggestion-surface");
                return;
            }
        }
        // Last resort: only use export-like candidate if literally nothing else matched
        if (fallbackExport != null) {
            SafeClick.click(fallbackExport, page);
            afterSelect(page, expectedApplicationUrl, value, "listbox-export-fallback");
            return;
        }
        TraceLogger.warn("AUTocomplete", "AUTOCOMPLETE_CONFIRM_SKIPPED", "No autocomplete option selected", TraceMeta.of(
                "value", value
        ));
    }

    private static void afterSelect(Page page, String expectedApplicationUrl, String value, String strategy) {
        TraceLogger.info("AUTocomplete", "AUTOCOMPLETE_CONFIRM_COMPLETED", "Selected autocomplete option", TraceMeta.of(
                "value", value, "strategy", strategy
        ));
        SafeClick.settle(page);
        if (expectedApplicationUrl != null && !expectedApplicationUrl.isBlank()) {
            HostContextGuard.restoreExpectedHostIfNeeded(page, expectedApplicationUrl);
        }
    }

    private static boolean isExportLikeCandidate(Locator candidate) {
        try {
            String text = safeText(candidate);
            Object href = candidate.evaluate("""
                    el => {
                      const a = el.closest('a') || (el.tagName && el.tagName.toLowerCase() === 'a' ? el : null);
                      const fromA = a ? (a.getAttribute('href') || '') : '';
                      const data = el.getAttribute('data-href') || el.getAttribute('href') || '';
                      return fromA || data || '';
                    }
                    """);
            String hrefText = href == null ? "" : String.valueOf(href);
            return HostContextGuard.looksLikeExternalTradeRedirect(text)
                    || HostContextGuard.looksLikeExternalTradeRedirect(hrefText);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean needsConfirmation(Locator input) {
        try {
            Object result = input.evaluate("""
                    el => {
                      const placeholder = (el.getAttribute('placeholder') || '').toLowerCase();
                      const autocomplete = (el.getAttribute('aria-autocomplete') || '').toLowerCase();
                      const expanded = el.getAttribute('aria-expanded');
                      return placeholder.includes('hint')
                          || placeholder.includes('type for')
                          || autocomplete === 'list'
                          || autocomplete === 'both'
                          || expanded === 'true';
                    }
                    """);
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean selectVisible(Locator options) {
        for (int i = 0; i < options.count(); i++) {
            if (options.nth(i).isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyVisibleSuggestion(Page page) {
        Locator suggestions = page.locator("[role='listbox'] [role='option'], [role='option'], [role='listbox'] > *");
        for (int i = 0; i < Math.min(suggestions.count(), 10); i++) {
            if (suggestions.nth(i).isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static boolean textContainsAnyToken(Locator locator, String value) {
        for (String token : value.split("\\s+")) {
            if (token.length() >= 3 && textContains(locator, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean textContains(Locator locator, String value) {
        String text = safeText(locator).toLowerCase(Locale.ROOT);
        String needle = value.toLowerCase(Locale.ROOT);
        return text.contains(needle);
    }

    private static boolean isSameNode(Locator left, Locator right) {
        try {
            Object same = left.evaluate("(el, other) => el === other", right);
            return Boolean.TRUE.equals(same);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String safeText(Locator locator) {
        try {
            String text = locator.innerText(new Locator.InnerTextOptions().setTimeout(1000));
            return text == null ? "" : text.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeTag(Locator locator) {
        try {
            Object tag = locator.evaluate("el => (el.tagName || '').toLowerCase()");
            return tag == null ? "" : tag.toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
