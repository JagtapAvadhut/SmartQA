package com.smartqa.browser;

import java.util.Locale;
import java.util.Set;

/**
 * Honest expected-vs-actual classification. Never rewrites the expected assertion.
 * Distinguishes engine/page problems from a live app that simply showed different text.
 */
public final class AssertionTruthEngine {

    public enum Outcome {
        ASSERTION_PASS,
        ASSERTION_FAIL,
        BUSINESS_STATE_MISMATCH,
        NOT_REACHED,
        WRONG_PAGE,
        LOGIN_STATE_FAILURE
    }

    public record Verdict(
            Outcome outcome,
            String expected,
            String actual,
            String reason
    ) {
        public String userMessage() {
            return "EXPECTED:\n" + nullToEmpty(expected)
                    + "\n\nACTUAL:\n" + nullToEmpty(actual)
                    + "\n\nSTATUS:\n" + outcome.name()
                    + "\n\n" + nullToEmpty(reason);
        }
    }

    private static final Set<String> LOGIN_HINTS = Set.of("/auth/login", "/login", "/signin", "/sign-in");

    private AssertionTruthEngine() {
    }

    public static Verdict evaluate(String expected, String actualVisible, String url, String title) {
        String exp = expected == null ? "" : expected.trim();
        String actual = actualVisible == null ? "" : actualVisible.trim();
        String actualNorm = normalize(actual);
        String expNorm = normalize(exp);
        String href = url == null ? "" : url.toLowerCase(Locale.ROOT);
        String titleNorm = normalize(title);

        if (exp.isBlank()) {
            return new Verdict(Outcome.ASSERTION_FAIL, exp, actual, "Expected assertion text was empty.");
        }
        if (!expNorm.isBlank() && (actualNorm.contains(expNorm) || titleNorm.contains(expNorm))) {
            return new Verdict(Outcome.ASSERTION_PASS, exp, actual, "Expected text is visible.");
        }
        for (String variant : VerifyExpectation.textVariants(exp)) {
            String variantNorm = normalize(variant);
            if (!variantNorm.isBlank() && (actualNorm.contains(variantNorm) || titleNorm.contains(variantNorm))) {
                return new Verdict(Outcome.ASSERTION_PASS, exp, actual,
                        "Expected text is visible (accepted inflection: " + variant + ").");
            }
        }
        if (looksLikeLoginUrl(href) && looksLikeAuthenticatedExpectation(exp)) {
            return new Verdict(Outcome.LOGIN_STATE_FAILURE, exp, actual,
                    "Login click or submit completed but the browser is still on the login page.");
        }
        if (looksLikeLoginUrl(href) && !looksLikeLoginExpectation(exp)) {
            return new Verdict(Outcome.NOT_REACHED, exp, actual,
                    "Assertion was not reached because the session is still on a login page.");
        }
        String competing = competingVisibleMessage(exp, actual);
        if (competing != null) {
            return new Verdict(Outcome.BUSINESS_STATE_MISMATCH, exp, competing,
                    "The live application produced a different business message than the requested assertion. "
                            + "The assertion was not changed.");
        }
        return new Verdict(Outcome.ASSERTION_FAIL, exp, actual,
                "Expected text was not visible in the current page.");
    }

    public static boolean looksLikeLoginUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        for (String hint : LOGIN_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeAuthenticatedExpectation(String expected) {
        String n = normalize(expected);
        return n.contains("dashboard")
                || n.contains("employee list")
                || n.contains("pim")
                || n.contains("welcome");
    }

    private static boolean looksLikeLoginExpectation(String expected) {
        String n = normalize(expected);
        return n.contains("login") || n.contains("username") || n.contains("password") || n.contains("sign in");
    }

    /**
     * When the app shows a related but different validation/heading, that is business truth, not a locator miss.
     */
    static String competingVisibleMessage(String expected, String actual) {
        if (actual == null || actual.isBlank() || expected == null) {
            return null;
        }
        String exp = normalize(expected);
        String act = actual.replaceAll("\\s+", " ").trim();
        if (act.isBlank() || normalize(act).contains(exp)) {
            return null;
        }
        boolean passwordFamily = exp.contains("password")
                && (act.toLowerCase(Locale.ROOT).contains("password")
                || act.toLowerCase(Locale.ROOT).contains("credential"));
        boolean locationFamily = (exp.contains("mumbai") || exp.contains("location") || exp.contains("near"))
                && (act.toLowerCase(Locale.ROOT).contains("nagpur")
                || act.toLowerCase(Locale.ROOT).contains("near"));
        boolean searchFamily = (exp.contains("samsung") && act.toLowerCase(Locale.ROOT).contains("micromax"))
                || (exp.contains("hp") && act.toLowerCase(Locale.ROOT).contains("brand"));
        if (passwordFamily || locationFamily || searchFamily) {
            return firstSentence(act);
        }
        return null;
    }

    private static String firstSentence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.replaceAll("\\s+", " ").trim();
        int end = trimmed.indexOf('.');
        if (end > 20 && end < 240) {
            return trimmed.substring(0, end + 1);
        }
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240) + "…";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
