package com.smartqa.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Specialized FILTER failure analysis.
 */
@Component
public class FilterFailureAnalyzer {

    public AiDiagnosticResult analyze(FailureEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        String blob = (safe(evidence.exception()) + " " + safe(evidence.actual()) + " "
                + safe(evidence.failureCategory()) + " " + safe(evidence.action())
                + " " + safe(evidence.target())).toLowerCase(Locale.ROOT);
        if (!blob.contains("filter") && !blob.contains("chip") && !blob.contains("accordion")
                && !blob.contains("related categor") && !"FILTER".equalsIgnoreCase(evidence.failureCategory())) {
            return null;
        }

        String sub;
        String root;
        String explanation;
        if (blob.contains("not found") || blob.contains("no candidate")) {
            sub = "FILTER_NOT_FOUND";
            root = "FILTER_NOT_FOUND";
            explanation = "Filter control or option could not be discovered in the current DOM.";
        } else if (blob.contains("not open") || blob.contains("collapsed") || blob.contains("expand")) {
            sub = "FILTER_NOT_OPEN";
            root = "FILTER_NOT_OPEN";
            explanation = "Filter panel appears closed; option was not reachable.";
        } else if (blob.contains("option")) {
            sub = "FILTER_OPTION_NOT_FOUND";
            root = "FILTER_OPTION_NOT_FOUND";
            explanation = "Requested filter option was not found or not selectable.";
        } else if (blob.contains("state") || blob.contains("not applied") || blob.contains("not selected")) {
            sub = "FILTER_STATE_NOT_CHANGED";
            root = "FILTER_NOT_SELECTED";
            explanation = "Filter interaction did not change selected state or results.";
        } else {
            sub = "WRONG_FILTER_CONTEXT";
            root = "WRONG_FILTER_CONTEXT";
            explanation = "Filter action ran in an unexpected page or panel context.";
        }

        AiDiagnosticResult result = AiDiagnosticResult.fallback("FILTER", root, explanation, 0.82);
        result.setFilterSubCategory(sub);
        result.setResponsibleSubsystem("Filter Intelligence Engine");
        result.setRecoveryOptions(List.of(
                option("OPEN_CONTROL", "Expand filter panels before selecting", true, 0.8),
                option("RE_APPLY_FILTER", "Rediscover filter option and re-apply", true, 0.85),
                option("REFRESH_DOM", "Refresh DOM after panel expansion", true, 0.75)
        ));
        return result;
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
