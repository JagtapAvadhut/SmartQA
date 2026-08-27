package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.intent.SupportedActions;

import java.util.Locale;
import java.util.Map;

/**
 * Classifies before/after {@link StateSnapshot} diffs. Contradiction rejects PASS.
 */
public final class StateTransitionVerifier {

    public enum Classification {
        EXPECTED_CHANGE,
        EXPECTED_NO_CHANGE,
        UNEXPECTED_CHANGE,
        NO_CHANGE,
        CONTRADICTORY_CHANGE
    }

    public record Signals(
            boolean expectChange,
            boolean successVisible,
            boolean errorVisible,
            boolean loginFormStillVisible,
            boolean intendedStatePresent
    ) {
        public static Signals unknown(boolean expectChange) {
            return new Signals(expectChange, false, false, false, false);
        }
    }

    public record Verdict(Classification classification, String reason, boolean passAllowed) {
    }

    private StateTransitionVerifier() {
    }

    public static Verdict classify(StateSnapshot.Diff diff, Signals signals) {
        Signals sig = signals == null ? Signals.unknown(true) : signals;
        boolean changed = diff != null && diff.meaningfullyChanged();

        if (sig.successVisible() && sig.errorVisible()) {
            return new Verdict(Classification.CONTRADICTORY_CHANGE,
                    "Success and error signals are both visible after the action", false);
        }
        if (sig.expectChange() && sig.loginFormStillVisible() && !changed) {
            if (sig.errorVisible()) {
                return new Verdict(Classification.EXPECTED_NO_CHANGE,
                        "Authentication or validation error kept the login form visible", true);
            }
            return new Verdict(Classification.CONTRADICTORY_CHANGE,
                    "Login was expected to leave the form, but the login form is still visible", false);
        }
        if (!sig.expectChange()) {
            if (changed && sig.errorVisible()) {
                return new Verdict(Classification.CONTRADICTORY_CHANGE,
                        "No change was expected but an error state appeared", false);
            }
            if (changed) {
                return new Verdict(Classification.UNEXPECTED_CHANGE,
                        "State changed when the action expected no change", true);
            }
            return new Verdict(Classification.EXPECTED_NO_CHANGE, "State unchanged as expected", true);
        }
        if (sig.intendedStatePresent() && !sig.errorVisible()) {
            return new Verdict(changed ? Classification.EXPECTED_CHANGE : Classification.EXPECTED_NO_CHANGE,
                    "Intended state is already present", true);
        }
        if (!changed) {
            return new Verdict(Classification.NO_CHANGE, "No meaningful state change after action", true);
        }
        if (sig.errorVisible() && !sig.successVisible()) {
            return new Verdict(Classification.UNEXPECTED_CHANGE, "Error signal visible after expected change", true);
        }
        return new Verdict(Classification.EXPECTED_CHANGE, "Meaningful state change observed", true);
    }

    public static Signals inspect(Page page, String action) {
        return inspect(page, action, null);
    }

    /**
     * Live signals after an action. A password field remaining on screen is only a login-form
     * contradiction when the action was an authentication submit (click Login / Sign in).
     * Filling username/password, or a validation error after a negative login, must not
     * be classified as "login should have left the form".
     */
    public static Signals inspect(Page page, String action, String target) {
        boolean expectChange = expectsChange(action);
        boolean authSubmit = isAuthenticationSubmit(action, target);
        if (page == null) {
            return Signals.unknown(expectChange);
        }
        try {
            Object raw = page.evaluate("""
                    () => {
                      const text = ((document.body && document.body.innerText) || '').toLowerCase();
                      const errorEl = document.querySelector(
                        '[role="alert"], .error, .toast-error, [class*="error"], .MuiAlert-standardError, [aria-live="assertive"]'
                      );
                      const successEl = document.querySelector(
                        '.success, .toast-success, [class*="success"], .MuiAlert-standardSuccess'
                      );
                      const visibleError = !!(errorEl && errorEl.offsetParent !== null);
                      const error = visibleError
                        || (/\\b(error|invalid|failed|unable to|required)\\b/.test(text) && visibleError);
                      const success = !!(successEl && successEl.offsetParent !== null)
                        || /\\b(success|saved|logged in|added to cart)\\b/.test(text);
                      const passwordPresent = !!document.querySelector(
                        'input[type="password"], form[action*="login"] input'
                      );
                      return { error, success, passwordPresent };
                    }
                    """);
            boolean error = false;
            boolean success = false;
            boolean passwordPresent = false;
            if (raw instanceof Map<?, ?> map) {
                error = Boolean.TRUE.equals(map.get("error"));
                success = Boolean.TRUE.equals(map.get("success"));
                passwordPresent = Boolean.TRUE.equals(map.get("passwordPresent"));
            }
            boolean loginForm = authSubmit && passwordPresent && !error;
            return new Signals(expectChange, success, error, loginForm, false);
        } catch (RuntimeException ex) {
            return Signals.unknown(expectChange);
        }
    }

    /**
     * True only for submit-to-authenticate actions, not for typing into a login form
     * and not for unrelated clicks on a page that happens to contain a password field.
     */
    static boolean isAuthenticationSubmit(String action, String target) {
        String a = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String t = target == null ? "" : target.toLowerCase(Locale.ROOT).trim();
        if (a.equals("input") || a.equals("type") || a.equals("fill") || a.equals("type_text")
                || a.equals("verify") || a.equals("assert") || a.equals("wait")) {
            return false;
        }
        boolean clickLike = a.equals("click") || a.equals("submit") || a.equals("login")
                || a.contains("click");
        if (!clickLike) {
            return false;
        }
        return t.equals("login") || t.equals("log in") || t.equals("log-in")
                || t.equals("signin") || t.equals("sign in") || t.equals("sign-in")
                || t.equals("logon") || t.equals("log on")
                || t.endsWith(" login") || t.endsWith(" sign in")
                || t.contains("login button") || t.contains("sign in button")
                || a.equals("login");
    }

    public static Verdict verify(StateSnapshot before, StateSnapshot after, Signals signals) {
        StateSnapshot.Diff diff = StateSnapshot.diff(before, after);
        Verdict verdict = classify(diff, signals);
        TraceLogger.info("BROWSER", "STATE_TRANSITION", verdict.reason(), TraceMeta.of(
                "classification", verdict.classification().name(),
                "passAllowed", verdict.passAllowed(),
                "changed", diff.meaningfullyChanged(),
                "urlChanged", diff.urlChanged(),
                "expectChange", signals == null || signals.expectChange()
        ));
        return verdict;
    }

    /**
     * DOM hash mutation alone is not a proven widget outcome. Callers should also set
     * {@link Signals#intendedStatePresent()} when checkbox/input/selection/URL/modal actually matched.
     */
    public static boolean meaningfulOutcomeObserved(Verdict verdict, Signals signals) {
        if (verdict == null || !verdict.passAllowed()) {
            return false;
        }
        if (verdict.classification() == Classification.CONTRADICTORY_CHANGE) {
            return false;
        }
        if (signals != null && signals.intendedStatePresent()) {
            return true;
        }
        return verdict.classification() == Classification.EXPECTED_CHANGE
                || verdict.classification() == Classification.EXPECTED_NO_CHANGE;
    }

    public static Signals withIntendedState(Signals base, boolean intended) {
        Signals sig = base == null ? Signals.unknown(true) : base;
        return new Signals(
                sig.expectChange(),
                sig.successVisible(),
                sig.errorVisible(),
                sig.loginFormStillVisible(),
                intended);
    }

    /**
     * Widget-level proof: input value, checked/selected, aria-expanded. DOM mutation is not enough.
     */
    public static boolean widgetStateMatches(Page page, Locator locator, String action, String expectedValue) {
        if (locator == null || action == null) {
            return false;
        }
        try {
            String a = SupportedActions.canonicalize(action);
            if (SupportedActions.INPUT.equals(a) || SupportedActions.SEARCH.equals(a)) {
                if (expectedValue == null || expectedValue.isBlank()) {
                    return true;
                }
                String current = locator.inputValue();
                return current != null && current.toLowerCase(Locale.ROOT)
                        .contains(expectedValue.toLowerCase(Locale.ROOT));
            }
            if (SupportedActions.CHECKBOX.equals(a) || SupportedActions.RADIO.equals(a)) {
                boolean want = !"false".equalsIgnoreCase(expectedValue);
                return locator.isChecked() == want;
            }
            if (SupportedActions.SELECT.equals(a)) {
                if (expectedValue == null || expectedValue.isBlank()) {
                    return true;
                }
                String text = firstNonBlank(locator.inputValue(), locator.innerText());
                return text.toLowerCase(Locale.ROOT).contains(expectedValue.toLowerCase(Locale.ROOT));
            }
            if (SupportedActions.EXPAND.equals(a)) {
                return "true".equalsIgnoreCase(locator.getAttribute("aria-expanded"));
            }
            if (SupportedActions.COLLAPSE.equals(a)) {
                String expanded = locator.getAttribute("aria-expanded");
                return expanded == null || "false".equalsIgnoreCase(expanded);
            }
            return locator.isVisible();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static boolean expectsChange(String action) {
        if (action == null || action.isBlank()) {
            return true;
        }
        String a = action.toLowerCase(Locale.ROOT);
        return !a.contains("verify") && !a.contains("assert") && !a.contains("wait");
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
