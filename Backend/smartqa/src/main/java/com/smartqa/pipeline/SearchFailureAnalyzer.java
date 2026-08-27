package com.smartqa.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Specialized SEARCH failure analysis. Deterministic first; feeds AI when ambiguous.
 */
@Component
public class SearchFailureAnalyzer {

    public AiDiagnosticResult analyze(FailureEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        String blob = (safe(evidence.exception()) + " " + safe(evidence.actual()) + " "
                + safe(evidence.url()) + " " + safe(evidence.failureCategory()) + " "
                + safe(evidence.action()) + " " + String.join(" ", evidence.previousAttempts() == null
                ? List.of() : evidence.previousAttempts())).toLowerCase(Locale.ROOT);

        boolean searchish = blob.contains("search") || blob.contains("autocomplete")
                || blob.contains("suggestion") || "SEARCH".equalsIgnoreCase(evidence.failureCategory())
                || blob.contains("host_mismatch") || blob.contains("export.");

        if (!searchish && !blob.contains("wrong_host") && !blob.contains("wrong host")) {
            return null;
        }

        String currentHost = FailureEvidenceCollector.hostOf(evidence.url());
        if (currentHost.contains("export.") || blob.contains("host_mismatch") || blob.contains("export.")) {
            AiDiagnosticResult result = AiDiagnosticResult.fallback(
                    "WRONG_HOST",
                    "WRONG_RESULT_HOST",
                    "The browser was redirected to a different application host, so the expected domestic search/results flow was never reached. The original assertion must remain unchanged.",
                    0.94);
            result.setSearchSubCategory("WRONG_HOST");
            result.setResponsibleSubsystem("Search/Navigation state");
            List<RecoveryOption> options = new ArrayList<>();
            options.add(option("RESTORE_EXPECTED_HOST", "Return to the requested application host before re-running search", true, 0.95));
            options.add(option("RESELECT_AUTOCOMPLETE", "Re-select a suggestion that stays on the domestic host", true, 0.85));
            options.add(option("RESEARCH_SEARCH_RESULT", "Re-apply search and verify result heading on expected host", true, 0.85));
            result.setRecoveryOptions(options);
            return result;
        }

        if (blob.contains("autocomplete") || blob.contains("suggestion")) {
            AiDiagnosticResult result = AiDiagnosticResult.fallback(
                    "SEARCH",
                    "WRONG_SUGGESTION",
                    "Search suggestion selection did not complete on the expected results context.",
                    0.8);
            result.setSearchSubCategory("WRONG_SUGGESTION");
            result.setResponsibleSubsystem("Search Intelligence Engine");
            result.setRecoveryOptions(List.of(
                    option("RESELECT_AUTOCOMPLETE", "Wait for suggestions and select the semantic match", true, 0.8),
                    option("REFRESH_DOM", "Refresh DOM after typing before selecting", true, 0.7)
            ));
            return result;
        }

        if (blob.contains("search_state_mismatch") || blob.contains("search state mismatch")
                || (blob.contains("samsung") && blob.contains("micromax"))) {
            AiDiagnosticResult result = AiDiagnosticResult.fallback(
                    "SEARCH_STATE_MISMATCH",
                    "SEARCH_STATE_MISMATCH",
                    "Requested search semantics are not present in the selected suggestion or result context.",
                    0.9);
            result.setSearchSubCategory("SEARCH_STATE_MISMATCH");
            result.setResponsibleSubsystem("Search Intelligence Engine");
            result.setRecoveryOptions(List.of(
                    option("RESELECT_AUTOCOMPLETE", "Reselect the suggestion that matches the requested search tokens", true, 0.88),
                    option("RESEARCH_SEARCH_RESULT", "Retry search and verify result heading/input/URL state", true, 0.85)
            ));
            return result;
        }
        if (blob.contains("location_state_mismatch") || blob.contains("location state mismatch")
                || (blob.contains("mumbai") && blob.contains("nagpur"))) {
            AiDiagnosticResult result = AiDiagnosticResult.fallback(
                    "LOCATION_STATE_MISMATCH",
                    "LOCATION_STATE_MISMATCH",
                    "Requested location is not the selected or visible location on the page.",
                    0.92);
            result.setSearchSubCategory("LOCATION_STATE_MISMATCH");
            result.setResponsibleSubsystem("Search Intelligence Engine");
            result.setRecoveryOptions(List.of(
                    option("RESELECT_AUTOCOMPLETE", "Reselect the requested location suggestion", true, 0.9),
                    option("REFRESH_DOM", "Refresh DOM and verify visible selected location", true, 0.75)
            ));
            return result;
        }
        if (blob.contains("search")) {
            AiDiagnosticResult result = AiDiagnosticResult.fallback(
                    "SEARCH",
                    "SEARCH_NOT_COMPLETED",
                    "Search input or result selection did not complete.",
                    0.72);
            result.setSearchSubCategory("SEARCH_NOT_COMPLETED");
            result.setResponsibleSubsystem("Search Intelligence Engine");
            result.setRecoveryOptions(List.of(
                    option("RESEARCH_SEARCH_RESULT", "Retry search with fresh DOM and suggestion wait", true, 0.75),
                    option("WAIT_FOR_STATE", "Wait for search results to settle", true, 0.7)
            ));
            return result;
        }
        return null;
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
