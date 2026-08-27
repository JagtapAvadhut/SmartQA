package com.smartqa.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates AI recovery recommendations before the deterministic engine may execute them.
 */
public final class RecoveryPlanValidator {

    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.55;

    private static final Set<String> SUPPORTED = Set.of(
            "REFRESH_DOM",
            "REDISCOVER_ELEMENT",
            "RE_RANK_CANDIDATES",
            "RE_RESOLVE",
            "OPEN_CONTROL",
            "CLOSE_OVERLAY",
            "WAIT_FOR_STATE",
            "RESEARCH_SEARCH_RESULT",
            "RESELECT_AUTOCOMPLETE",
            "RESTORE_EXPECTED_HOST",
            "RE_NAVIGATE",
            "RE_APPLY_FILTER",
            "VERIFY_ASSERTION_CONTEXT",
            "REDISCOVER",
            "RETRY_STEP"
    );

    private RecoveryPlanValidator() {
    }

    public record ValidationResult(
            boolean accepted,
            RecoveryOption option,
            String rejectReason
    ) {
        public static ValidationResult accept(RecoveryOption option) {
            return new ValidationResult(true, option, null);
        }

        public static ValidationResult reject(RecoveryOption option, String reason) {
            return new ValidationResult(false, option, reason);
        }
    }

    public static List<ValidationResult> validateAll(
            AiDiagnosticResult diagnosis,
            FailureEvidence evidence,
            String expectedApplicationUrl,
            double confidenceThreshold) {
        List<ValidationResult> results = new ArrayList<>();
        if (diagnosis == null || diagnosis.recoveryOptions().isEmpty()) {
            return results;
        }
        double overall = diagnosis.confidence();
        if (overall < confidenceThreshold) {
            for (RecoveryOption option : diagnosis.recoveryOptions()) {
                results.add(ValidationResult.reject(option, "AI confidence below threshold: " + overall));
            }
            return results;
        }
        for (RecoveryOption option : diagnosis.recoveryOptions()) {
            results.add(validateOne(option, evidence, expectedApplicationUrl, confidenceThreshold));
        }
        return results;
    }

    public static ValidationResult validateOne(
            RecoveryOption option,
            FailureEvidence evidence,
            String expectedApplicationUrl,
            double confidenceThreshold) {
        if (option == null || option.type() == null || option.type().isBlank()) {
            return ValidationResult.reject(option, "Missing recovery type");
        }
        String type = option.type().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(type)) {
            return ValidationResult.reject(option, "Unsupported recovery type: " + type);
        }
        if (!option.safe()) {
            return ValidationResult.reject(option, "AI marked recovery as unsafe");
        }
        if (option.confidence() > 0 && option.confidence() < confidenceThreshold) {
            return ValidationResult.reject(option, "Option confidence below threshold");
        }
        if (containsArbitraryScript(option.reason()) || containsArbitraryScript(option.targetHint())) {
            return ValidationResult.reject(option, "Arbitrary script/CSS not allowed");
        }
        if ("RE_NAVIGATE".equals(type) || "RESTORE_EXPECTED_HOST".equals(type)) {
            String domainHint = option.domainHint();
            String expectedHost = FailureEvidenceCollector.hostOf(expectedApplicationUrl);
            String currentHost = evidence == null ? "" : FailureEvidenceCollector.hostOf(evidence.url());
            if (domainHint != null && !domainHint.isBlank()) {
                String hintHost = FailureEvidenceCollector.hostOf(
                        domainHint.contains("://") ? domainHint : "https://" + domainHint);
                if (!hintHost.isBlank() && !expectedHost.isBlank()
                        && !FailureEvidenceCollector.sameRegistrableDomain(hintHost, expectedHost)
                        && !hintHost.equalsIgnoreCase(currentHost)) {
                    return ValidationResult.reject(option, "External navigation outside requested domain");
                }
            }
        }
        return ValidationResult.accept(option);
    }

    private static boolean containsArbitraryScript(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("<script")
                || lower.contains("javascript:")
                || lower.contains("eval(")
                || lower.contains("document.write")
                || lower.contains("xpath=")
                || (lower.contains("css=") && lower.contains("{"));
    }
}
