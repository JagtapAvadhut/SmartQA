package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.browser.intelligence.ActionCompatibility;
import com.smartqa.browser.intelligence.ControlClassifier;
import com.smartqa.browser.intelligence.ControlType;
import com.smartqa.browser.intelligence.StateSnapshot;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.intent.SupportedActions;

/**
 * Generic search boundary: input → suggestions → submit → result state.
 * Does not assume fill+Enter alone means success.
 */
public final class SearchIntelligence {

    public record Result(boolean suggestionSelected, boolean submitted, boolean stateChanged, String strategy) {
    }

    private SearchIntelligence() {
    }

    public static Result execute(Page page, Locator input, String value, String expectedApplicationUrl) {
        if (page == null || input == null || value == null || value.isBlank()) {
            return new Result(false, false, false, "noop");
        }
        prepareFillableSearchField(page, input);
        StateSnapshot before = StateSnapshot.capture(page, 0);
        input.fill(value);
        if (isInsideFilterPanel(input)) {
            TraceLogger.info("SEARCH", "SEARCH_STATE_CHANGED", "In-panel filter search filled without page submit", TraceMeta.of(
                    "value", value,
                    "strategy", "panel-filter"
            ));
            return new Result(false, false, true, "panel-filter");
        }
        AutocompleteHandler.confirmSelectionIfNeeded(page, input, value, expectedApplicationUrl);
        boolean suggestionLikely = !before.url().equals(safeUrl(page))
                || !before.visibleTextFingerprint().equals(StateSnapshot.capture(page, 0).visibleTextFingerprint());

        StateSnapshot afterSuggest = StateSnapshot.capture(page, 0);
        boolean changedAfterSuggest = before.meaningfullyDifferent(afterSuggest);

        if (changedAfterSuggest && HostContextGuard.hostDiverged(expectedApplicationUrl, safeUrl(page))) {
            HostContextGuard.restoreExpectedHostIfNeeded(page, expectedApplicationUrl);
        }

        if (changedAfterSuggest) {
            TraceLogger.info("SEARCH", "SEARCH_STATE_CHANGED", "Search changed state after autocomplete", TraceMeta.of(
                    "value", value,
                    "strategy", "autocomplete"
            ));
            SearchStateContract.verifySearch(page, value);
            return new Result(true, false, true, "autocomplete");
        }

        boolean submitted = false;
        try {
            input.press("Enter");
            submitted = true;
        } catch (RuntimeException ignored) {
        }
        if (!submitted) {
            submitted = clickSearchButton(page, input);
        }

        StateSnapshot afterSubmit = StateSnapshot.capture(page, 0);
        boolean changed = afterSuggest.meaningfullyDifferent(afterSubmit) || before.meaningfullyDifferent(afterSubmit);
        if (HostContextGuard.hostDiverged(expectedApplicationUrl, safeUrl(page))) {
            HostContextGuard.restoreExpectedHostIfNeeded(page, expectedApplicationUrl);
            afterSubmit = StateSnapshot.capture(page, 0);
            changed = before.meaningfullyDifferent(afterSubmit);
        }

        TraceLogger.info("SEARCH", changed ? "SEARCH_STATE_CHANGED" : "SEARCH_STATE_NOT_CHANGED",
                "Search submission verified", TraceMeta.of(
                        "value", value,
                        "submitted", submitted,
                        "stateChanged", changed,
                        "strategy", submitted ? "enter-or-button" : "none"
                ));
        SearchStateContract.verifySearch(page, value);
        return new Result(suggestionLikely, submitted, changed, submitted ? "enter-or-button" : "none");
    }

    /**
     * Fail-fast on hidden/covered search fields. Never wait on a non-visible first match.
     * Overlay dismiss + click-to-focus are generic recoveries, not timeout increases.
     */
    static void prepareFillableSearchField(Page page, Locator input) {
        ControlType controlType = ControlClassifier.classify(input);
        if (controlType == ControlType.SEARCH_BUTTON
                || controlType == ControlType.BUTTON
                || controlType == ControlType.ICON_BUTTON
                || controlType == ControlType.HEADING
                || controlType == ControlType.LABEL
                || controlType == ControlType.TEXT) {
            throw new SmartQaException(ErrorCode.ACTION_ELEMENT_MISMATCH,
                    "Search cannot fill a button control");
        }
        if (ActionCompatibility.isCapabilityMismatch(SupportedActions.SEARCH, controlType)) {
            throw new SmartQaException(ErrorCode.ACTION_ELEMENT_MISMATCH,
                    ActionCompatibility.CAPABILITY_MISMATCH + ": search cannot use " + controlType);
        }
        BlockingOverlayGuard.dismissConsentBanners(page);
        BlockingOverlayGuard.dismissIfBlocking(page);
        ActionabilityVerifier.Result check = ActionabilityVerifier.verify(input, SupportedActions.SEARCH);
        if (!check.visible()) {
            throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE,
                    "Search field is not visible (fail-fast; hidden candidate skipped)");
        }
        if (check.covered()) {
            BlockingOverlayGuard.dismissCoveringElement(page, input);
            BlockingOverlayGuard.dismissIfBlocking(page);
            check = ActionabilityVerifier.verify(input, SupportedActions.SEARCH);
        }
        if (!check.ok()) {
            try {
                input.click(new Locator.ClickOptions().setTimeout(2_000));
            } catch (RuntimeException ignored) {
            }
            check = ActionabilityVerifier.verify(input, SupportedActions.SEARCH);
        }
        if (!check.ok()) {
            throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE,
                    "Search field not actionable: " + check.reason());
        }
    }

    private static boolean isInsideFilterPanel(Locator input) {
        try {
            Object inside = input.evaluate("""
                    el => !!(el.closest('[role="listbox"], [role="dialog"], [role="menu"], [class*="dropdown"], [class*="filter"], [class*="facet"]'))
                    """);
            return Boolean.TRUE.equals(inside);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean clickSearchButton(Page page, Locator input) {
        try {
            Locator nearby = input.locator("xpath=ancestor::*[self::form or self::div][1]//button[contains(translate(normalize-space(.),'SEARCH','search'),'search')]");
            if (nearby.count() > 0 && nearby.first().isVisible()) {
                SafeClick.click(nearby.first(), page);
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            Locator btn = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Search"));
            if (btn.count() > 0 && btn.first().isVisible()) {
                SafeClick.click(btn.first(), page);
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            Locator submit = page.locator("button[type='submit'], input[type='submit']").first();
            if (submit.count() > 0 && submit.isVisible()) {
                SafeClick.click(submit, page);
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static String safeUrl(Page page) {
        try {
            return page.url() == null ? "" : page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
