package com.smartqa.pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evidence-gated failure diagnosis for the autonomous pipeline.
 * Combines deterministic classification with optional AI diagnosis and recovery metadata.
 */
public record FailureDiagnosis(
        String whatFailed,
        String whyFailed,
        String responsibleComponent,
        String category,
        String evidence,
        boolean autoHealAttempted,
        int attemptsUsed,
        String recommendedAction,
        List<String> candidateHints,
        Map<String, Object> details,
        FailureEvidence failureEvidence,
        AiDiagnosticResult aiDiagnosis,
        List<String> recoveryAttempts,
        SourceFixProposal sourceFix,
        String rootCause,
        Double aiConfidence,
        boolean requiresSourceFix,
        boolean autoRecoveryAttempted,
        boolean autoRecoverySucceeded,
        String screenshotPath
) {
    public static FailureDiagnosis of(
            String whatFailed,
            String whyFailed,
            String responsibleComponent,
            String category,
            String evidence,
            boolean autoHealAttempted,
            int attemptsUsed,
            String recommendedAction) {
        return new FailureDiagnosis(
                whatFailed,
                whyFailed,
                responsibleComponent,
                category,
                evidence,
                autoHealAttempted,
                attemptsUsed,
                recommendedAction,
                List.of(),
                Map.of(),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                false,
                false,
                false,
                null);
    }

    public FailureDiagnosis withEnrichment(
            FailureEvidence failureEvidence,
            AiDiagnosticResult aiDiagnosis,
            List<String> recoveryAttempts,
            SourceFixProposal sourceFix,
            boolean autoRecoveryAttempted,
            boolean autoRecoverySucceeded,
            Map<String, Object> extraDetails) {
        Map<String, Object> merged = new HashMap<>();
        if (details != null) {
            merged.putAll(details);
        }
        if (extraDetails != null) {
            merged.putAll(extraDetails);
        }
        String why = whyFailed;
        String component = responsibleComponent;
        String cat = category;
        String action = recommendedAction;
        String root = rootCause;
        Double conf = aiConfidence;
        boolean needsFix = requiresSourceFix;
        if (aiDiagnosis != null) {
            if (aiDiagnosis.explanation() != null && !aiDiagnosis.explanation().isBlank()) {
                why = aiDiagnosis.explanation();
            }
            if (aiDiagnosis.responsibleSubsystem() != null && !aiDiagnosis.responsibleSubsystem().isBlank()) {
                component = aiDiagnosis.responsibleSubsystem();
            }
            if (aiDiagnosis.normalizedClassification() != null) {
                cat = aiDiagnosis.normalizedClassification();
            }
            root = aiDiagnosis.rootCause();
            conf = aiDiagnosis.confidence();
            needsFix = aiDiagnosis.requiresSourceFix() || sourceFix != null;
            if (aiDiagnosis.recoveryOptions() != null && !aiDiagnosis.recoveryOptions().isEmpty()) {
                action = aiDiagnosis.recoveryOptions().get(0).reason();
            }
        }
        if (sourceFix != null) {
            needsFix = true;
            component = sourceFix.component() == null ? component : sourceFix.component();
            action = "Fix available — use Fix & Rebuild, then Run Again.";
            merged.put("sourceFixId", sourceFix.id());
            merged.put("sourceFixStatus", sourceFix.status());
        }
        String screenshot = failureEvidence == null ? screenshotPath : failureEvidence.screenshotPath();
        String evidenceText = evidence;
        if (failureEvidence != null) {
            evidenceText = "url=" + nullSafe(failureEvidence.url())
                    + "; title=" + nullSafe(failureEvidence.pageTitle())
                    + "; expected=" + nullSafe(failureEvidence.expected())
                    + "; actual=" + nullSafe(failureEvidence.actual());
            String understood = (nullSafe(failureEvidence.action()) + " " + nullSafe(failureEvidence.target())).trim();
            if (!understood.isBlank()) {
                merged.put("understoodIntent", understood);
            }
            if (failureEvidence.actual() != null && !failureEvidence.actual().isBlank()) {
                merged.put("foundTarget", failureEvidence.actual());
            }
        }
        return new FailureDiagnosis(
                whatFailed,
                why,
                component,
                cat,
                evidenceText,
                autoHealAttempted || autoRecoveryAttempted,
                attemptsUsed,
                action,
                candidateHints == null ? List.of() : candidateHints,
                Map.copyOf(merged),
                failureEvidence,
                aiDiagnosis,
                recoveryAttempts == null ? List.of() : List.copyOf(recoveryAttempts),
                sourceFix,
                root,
                conf,
                needsFix,
                autoRecoveryAttempted,
                autoRecoverySucceeded,
                screenshot
        );
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
