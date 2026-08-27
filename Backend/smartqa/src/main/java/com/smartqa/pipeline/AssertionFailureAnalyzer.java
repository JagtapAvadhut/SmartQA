package com.smartqa.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Assertion failure diagnosis. Never rewrites or weakens the expected assertion.
 */
@Component
public class AssertionFailureAnalyzer {

    public AiDiagnosticResult analyze(FailureEvidence evidence, String deterministicCategory) {
        if (evidence == null) {
            return null;
        }
        String blob = (safe(evidence.exception()) + " " + safe(evidence.actual()) + " "
                + safe(evidence.failureCategory()) + " " + safe(deterministicCategory)).toLowerCase(Locale.ROOT);
        boolean assertionish = blob.contains("assert") || blob.contains("verify")
                || blob.contains("expected text") || blob.contains("was not found on page")
                || "ASSERTION".equalsIgnoreCase(deterministicCategory)
                || "ASSERTION".equalsIgnoreCase(evidence.failureCategory());
        if (!assertionish) {
            return null;
        }

        String expected = safe(evidence.expected());
        String url = safe(evidence.url()).toLowerCase(Locale.ROOT);
        String visible = safe(evidence.visibleTextExcerpt()).toLowerCase(Locale.ROOT);
        String host = FailureEvidenceCollector.hostOf(evidence.url());

        String sub;
        String root;
        String explanation;
        String classification = "ASSERTION";
        double confidence = 0.8;

        if (host.contains("export.") || blob.contains("host_mismatch")) {
            classification = "WRONG_HOST";
            sub = "ASSERTION_WRONG_PAGE";
            root = "WRONG_RESULT_HOST";
            explanation = "Assertion ran on a different host than the requested application flow. "
                    + "Do not change the expected text \"" + expected + "\".";
            confidence = 0.93;
        } else if (!expected.isBlank() && !visible.isBlank() && !visible.contains(expected.toLowerCase(Locale.ROOT))) {
            if (looksLikeDifferentBusinessMessage(visible, expected)) {
                classification = "BUSINESS_STATE_MISMATCH";
                sub = "BUSINESS_STATE_MISMATCH";
                root = "BUSINESS_STATE_MISMATCH";
                explanation = "EXPECTED: \"" + expected + "\". ACTUAL visible application text differs. "
                        + "Do not rewrite the assertion. Report the live application message honestly.";
                confidence = 0.9;
            } else if (looksLikeFormIncomplete(visible, expected)) {
                sub = "ASSERTION_NOT_REACHED";
                root = "FORM_COMPLETION";
                explanation = "Expected validation text was not visible; prior form actions may be incomplete. "
                        + "Keep assertion \"" + expected + "\" unchanged and recover form state if possible.";
                confidence = 0.84;
            } else if (url.contains("login") || url.contains("auth") || visible.contains("required")) {
                sub = "ASSERTION_STATE_NOT_READY";
                root = "APPLICATION_STATE";
                explanation = "Page/form state is not ready for the expected assertion text.";
                confidence = 0.78;
            } else {
                sub = "ASSERTION_TEXT_MISMATCH";
                root = "ASSERTION_ELEMENT_NOT_VISIBLE";
                explanation = "Expected text \"" + expected + "\" was not visible in the final browser state.";
                confidence = 0.76;
            }
        } else if (blob.contains("not found") || blob.contains("not visible")) {
            sub = "ASSERTION_ELEMENT_NOT_VISIBLE";
            root = "ASSERTION_ELEMENT_NOT_VISIBLE";
            explanation = "Assertion target text/element was not visible.";
            confidence = 0.75;
        } else {
            sub = "REAL_APPLICATION_FAILURE";
            root = "REAL_APPLICATION_FAILURE";
            explanation = "Expected application state was not observed after actions. Original assertion remains authoritative.";
            confidence = 0.7;
        }

        AiDiagnosticResult result = AiDiagnosticResult.fallback(classification, root, explanation, confidence);
        result.setAssertionSubCategory(sub);
        result.setResponsibleSubsystem(
                "WRONG_HOST".equals(classification) ? "Search/Navigation state" : "Assertion Engine");
        result.setRecoveryOptions(List.of(
                option("VERIFY_ASSERTION_CONTEXT", "Re-check URL/title/DOM before treating assertion as app failure", true, 0.8),
                option("RESTORE_EXPECTED_HOST", "If host diverged, restore expected application host then re-verify", true, 0.85),
                option("WAIT_FOR_STATE", "Wait for validation messages to appear after submit", true, 0.7)
        ));
        result.setRequiresSourceFix(false);
        return result;
    }

    private static boolean looksLikeDifferentBusinessMessage(String visibleLower, String expected) {
        String exp = expected == null ? "" : expected.toLowerCase(Locale.ROOT);
        // Password *field labels* are not a competing business message. Only a different
        // visible auth/validation error (e.g. Invalid credentials) is a mismatch.
        if (exp.contains("password") && hasCompetingAuthError(visibleLower, exp)) {
            return true;
        }
        return (exp.contains("mumbai") && visibleLower.contains("nagpur"))
                || (exp.contains("samsung") && visibleLower.contains("micromax"));
    }

    private static boolean hasCompetingAuthError(String visibleLower, String expectedLower) {
        if (visibleLower.contains(expectedLower)) {
            return false;
        }
        return visibleLower.contains("invalid credential")
                || visibleLower.contains("incorrect password")
                || visibleLower.contains("incorrect username")
                || visibleLower.contains("authentication failed")
                || (visibleLower.contains("invalid") && visibleLower.contains("password")
                && !visibleLower.contains("required"));
    }

    private static boolean looksLikeFormIncomplete(String visibleLower, String expected) {
        if (visibleLower.contains("required") || visibleLower.contains("fill out")
                || visibleLower.contains("incomplete")) {
            return true;
        }
        // Missing validation / mismatch messages often mean prior form steps did not complete.
        String exp = expected == null ? "" : expected.toLowerCase(Locale.ROOT);
        return exp.contains("password") && (visibleLower.contains("password") || visibleLower.contains("employee"));
    }

    private static RecoveryOption option(String type, String reason, boolean safe, double confidence) {
        RecoveryOption o = new RecoveryOption(type, reason, safe);
        o.setConfidence(confidence);
        return o;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
