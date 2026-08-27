package com.smartqa.pipeline;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coordinates FAILURE → FailureEvidence → AI Diagnosis → Safe Recovery Plan
 * → Live Browser Verification (Safety Gate) → Recovery → Retry.
 * Deterministic engine = browser truth; AI = structured reasoning only (never invents selectors
 * or weakens assertions). See docs/SMARTQA_TEAM_ORCHESTRATION.md.
 */
@Service
public class FailureAwareRecoveryService {

    private final FailureDiagnostician diagnostician;
    private final FailureEvidenceCollector evidenceCollector;
    private final AiFailureDiagnosticService aiDiagnosticService;
    private final SafeRecoveryExecutor recoveryExecutor;
    private final DevelopmentFixLoopService developmentFixLoopService;
    private final RuntimeAdaptationStore adaptationStore;

    public FailureAwareRecoveryService(
            FailureDiagnostician diagnostician,
            FailureEvidenceCollector evidenceCollector,
            AiFailureDiagnosticService aiDiagnosticService,
            SafeRecoveryExecutor recoveryExecutor,
            DevelopmentFixLoopService developmentFixLoopService,
            RuntimeAdaptationStore adaptationStore) {
        this.diagnostician = diagnostician;
        this.evidenceCollector = evidenceCollector;
        this.aiDiagnosticService = aiDiagnosticService;
        this.recoveryExecutor = recoveryExecutor;
        this.developmentFixLoopService = developmentFixLoopService;
        this.adaptationStore = adaptationStore;
    }

    public Mono<EnrichedFailure> enrich(
            PipelineRun run,
            String stage,
            String message,
            String expected,
            String actual,
            Integer stepNumber,
            String action,
            String target,
            String screenshotPath,
            List<String> previousAttemptSummaries) {
        FailureDiagnosis base = diagnostician.diagnose(
                stage,
                message,
                run.attempt(),
                run.maxAttempts());

        // Host-aware category upgrade before AI
        FailureEvidence evidence = evidenceCollector.fromPipelineFailure(
                run,
                stage,
                message,
                base.category(),
                expected,
                actual,
                stepNumber,
                action,
                target,
                screenshotPath,
                previousAttemptSummaries);
        evidence = evidenceCollector.enrichWithHostMismatch(evidence, run.applicationUrl());
        if ((evidence.actual() != null && evidence.actual().contains("host_mismatch"))
                || (evidence.url() != null && evidence.url().toLowerCase(Locale.ROOT).contains("export."))) {
            base = FailureDiagnosis.of(
                    base.whatFailed(),
                    "Browser navigated to a different application host than the requested domestic flow.",
                    "Search/Navigation state",
                    "WRONG_HOST",
                    base.evidence(),
                    base.autoHealAttempted(),
                    base.attemptsUsed(),
                    "Restore expected host, rediscover search/filter state, re-verify assertion unchanged."
            );
        }

        FailureEvidence finalEvidence = evidence;
        FailureDiagnosis finalBase = base;
        if (isDeterministicIntentFailure(stage, message, finalBase)) {
            TraceLogger.info(
                    "PIPELINE",
                    "INTENT_VALIDATION_FAST_FAIL",
                    "Skipping AI diagnosis for pre-browser intent validation failure",
                    TraceMeta.of("stage", stage, "category", finalBase.category()));
            return Mono.just(new EnrichedFailure(finalBase, Map.of(), false, null));
        }
        return aiDiagnosticService.diagnose(finalEvidence, finalBase.category(), run.attempt())
                .map(ai -> {
                    List<RecoveryPlanValidator.ValidationResult> validated = RecoveryPlanValidator.validateAll(
                            ai,
                            finalEvidence,
                            run.applicationUrl(),
                            adaptationStore.confidenceThreshold());
                    logSafetyGate(ai, validated);

                    SafeRecoveryExecutor.RecoveryOutcome outcome = recoveryExecutor.executePipelineHints(
                            validated,
                            run.applicationUrl(),
                            ai);
                    adaptationStore.applyRecoveryHints(outcome.hintsForNextAttempt());

                    List<String> attemptHistory = new ArrayList<>(
                            previousAttemptSummaries == null ? List.of() : previousAttemptSummaries);
                    attemptHistory.add(finalBase.category() + "/" + (ai.rootCause() == null ? "" : ai.rootCause()));

                    SourceFixProposal proposal = null;
                    boolean secondReview = run.attempt() >= run.maxAttempts()
                            || (!outcome.recovered() && run.attempt() >= 2);
                    if (secondReview || ai.requiresSourceFix()) {
                        proposal = developmentFixLoopService.proposeIfNeeded(
                                ai, finalEvidence, attemptHistory, run.applicationUrl());
                    }

                    Map<String, Object> details = new HashMap<>();
                    details.put("recoveryHints", outcome.hintsForNextAttempt());
                    details.put("recoveryActions", outcome.actionsTaken());
                    details.put("adaptationVersion", adaptationStore.version());
                    if (ai.requiresUserInput() && ai.userQuestion() != null && !ai.userQuestion().isBlank()) {
                        details.put("requiresUserInput", true);
                        details.put("userQuestion", ai.userQuestion());
                        details.put("userOptions", ai.userOptions());
                    }

                    FailureDiagnosis enriched = finalBase.withEnrichment(
                            finalEvidence,
                            ai,
                            outcome.actionsTaken(),
                            proposal,
                            outcome.attempted(),
                            outcome.recovered(),
                            details);

                    boolean shouldRetry = diagnostician.shouldAutoRetry(enriched, run.attempt(), run.maxAttempts())
                            || shouldRetryFromAi(ai, enriched);
                    if (ai.requiresUserInput()) {
                        shouldRetry = false;
                    }
                    if (proposal != null && run.attempt() >= run.maxAttempts()) {
                        shouldRetry = false;
                    }

                    String finalOutcome = ai.requiresUserInput()
                            ? "awaiting_user_clarification"
                            : proposal != null && !shouldRetry
                            ? "source_fix_proposed"
                            : shouldRetry
                            ? "retry_scheduled"
                            : outcome.recovered() ? "recovered" : "failed_no_retry";
                    TraceLogger.info("PIPELINE", "AI_STRATEGY_OUTCOME", "Safety gate and recovery outcome", TraceMeta.of(
                            "classification", ai.normalizedClassification(),
                            "confidence", ai.confidence(),
                            "recommendedStrategy", AiFailureDiagnosticService.strategySummary(ai),
                            "strategyAccepted", validated.stream().anyMatch(RecoveryPlanValidator.ValidationResult::accepted),
                            "recoveryTried", String.join(",", outcome.actionsTaken()),
                            "sourceChanged", proposal != null,
                            "finalOutcome", finalOutcome,
                            "whatFailed", enriched.whatFailed(),
                            "why", enriched.rootCause()
                    ));

                    return new EnrichedFailure(enriched, outcome.hintsForNextAttempt(), shouldRetry, proposal);
                });
    }

    private static void logSafetyGate(AiDiagnosticResult ai, List<RecoveryPlanValidator.ValidationResult> validated) {
        if (validated == null || validated.isEmpty()) {
            TraceLogger.info("PIPELINE", "AI_SAFETY_GATE", "No recovery strategies to gate", TraceMeta.of(
                    "classification", ai == null ? "" : ai.normalizedClassification(),
                    "confidence", ai == null ? 0 : ai.confidence(),
                    "recommendedStrategy", AiFailureDiagnosticService.strategySummary(ai),
                    "strategyAccepted", false,
                    "finalOutcome", "no_strategies"
            ));
            return;
        }
        String accepted = validated.stream()
                .filter(RecoveryPlanValidator.ValidationResult::accepted)
                .map(v -> v.option() == null ? "" : v.option().type())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(","));
        String rejected = validated.stream()
                .filter(v -> !v.accepted())
                .map(v -> {
                    String type = v.option() == null || v.option().type() == null ? "?" : v.option().type();
                    return type + ":" + (v.rejectReason() == null ? "rejected" : v.rejectReason());
                })
                .collect(Collectors.joining("; "));
        TraceLogger.info("PIPELINE", "AI_SAFETY_GATE", "Deterministic Safety Gate verified AI strategies against live evidence", TraceMeta.of(
                "classification", ai == null ? "" : ai.normalizedClassification(),
                "confidence", ai == null ? 0 : ai.confidence(),
                "recommendedStrategy", AiFailureDiagnosticService.strategySummary(ai),
                "strategyAccepted", !accepted.isBlank(),
                "acceptedStrategies", accepted,
                "rejectedStrategies", rejected,
                "finalOutcome", accepted.isBlank() ? "all_rejected" : "partial_or_full_accept"
        ));
    }

    private static boolean isDeterministicIntentFailure(String stage, String message, FailureDiagnosis diagnosis) {
        String category = diagnosis == null || diagnosis.category() == null ? "" : diagnosis.category();
        if ("INTENT_VALIDATION".equals(category)) {
            return true;
        }
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String st = stage == null ? "" : stage.toUpperCase(Locale.ROOT);
        return "UNDERSTAND".equals(st)
                && (text.contains("locator-like") || text.contains("implementation details"));
    }

    private boolean shouldRetryFromAi(AiDiagnosticResult ai, FailureDiagnosis diagnosis) {
        if (ai == null) {
            return false;
        }
        String cat = diagnosis.category() == null ? ai.normalizedClassification() : diagnosis.category();
        return switch (cat == null ? "" : cat.toUpperCase(Locale.ROOT)) {
            case "WRONG_HOST", "WRONG_PAGE", "WRONG_STATE", "SEARCH", "SEARCH_STATE_MISMATCH",
                 "LOCATION_STATE_MISMATCH", "FILTER",
                 "WAIT_STATE", "ACTIONABILITY", "DOM_DISCOVERY", "LOCATOR" -> true;
            case "ASSERTION" -> {
                // Retry assertion only when wrong host/page/state — not to weaken text
                String sub = ai.assertionSubCategory() == null ? "" : ai.assertionSubCategory();
                String root = ai.rootCause() == null ? "" : ai.rootCause();
                yield sub.contains("WRONG") || root.contains("HOST") || root.contains("FORM_COMPLETION")
                        || root.contains("APPLICATION_STATE") || "WRONG_HOST".equals(ai.normalizedClassification());
            }
            default -> false;
        };
    }

    public record EnrichedFailure(
            FailureDiagnosis diagnosis,
            Map<String, Object> recoveryHints,
            boolean shouldRetry,
            SourceFixProposal sourceFix
    ) {
    }
}
