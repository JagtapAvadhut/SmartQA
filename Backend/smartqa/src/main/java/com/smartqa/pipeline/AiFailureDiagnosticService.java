package com.smartqa.pipeline;

import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.AiTelemetry;
import com.smartqa.ai.FallbackAiProvider;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.rag.RagFeedbackService;
import com.smartqa.rag.RagRetrievalResult;
import com.smartqa.rag.RagRetrievalService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Team reasoning path for ambiguous or failed steps.
 * Builds a fresh {@link AiEvidenceBundle} (screenshot + DOM + a11y + candidates) before every AI call.
 * Gemini is primary multimodal reasoning; Ollama is fallback / second opinion via {@link ConsensusResolver}.
 * Vector RAG supplies advisory context only — live browser evidence remains authoritative.
 * Returns structured strategies only — never invents selectors, never changes assertions,
 * never executes Playwright directly.
 */
@Service
public class AiFailureDiagnosticService {

    private static final String SYSTEM = """
            You are SmartQA's multimodal failure diagnostician (Gemini/Ollama team member).
            You receive a fresh screenshot (when available), compact DOM, accessibility data,
            candidates, URL/title, failed instruction/action, previous recovery attempts,
            and optional vector RAG memories (advisory only).
            Classify the failure and suggest SAFE recovery strategies.
            Return STRICT JSON only with fields:
            classification, rootCause, confidence (0-1), explanation,
            recommendedCandidateId (candidate-A / candidate-B … or null),
            recoveryOptions (array of {type, reason, safe, confidence}),
            requiresUserInput, requiresSourceFix, userQuestion, userOptions,
            assertionSubCategory, searchSubCategory, filterSubCategory, responsibleSubsystem.

            classification must be one of:
            USER_INSTRUCTION, AI_INTENT, DOM_DISCOVERY, LOCATOR, ACTIONABILITY, WAIT_STATE,
            SEARCH, FILTER, ASSERTION, BROWSER, ENVIRONMENT, APPLICATION, GENERATED_TEST,
            VALIDATOR, WRONG_PAGE, WRONG_HOST, WRONG_STATE, AMBIGUOUS_TARGET, AMBIGUOUS_ELEMENT,
            UNKNOWN, GENERIC_ENGINE_DEFECT,
            SEARCH_STATE_MISMATCH, LOCATION_STATE_MISMATCH, FILTER_STATE_MISMATCH, WRONG_CANDIDATE,
            FILTER_NOT_FOUND, FILTER_NOT_OPEN, FILTER_OPTION_NOT_FOUND, FILTER_NOT_SELECTED,
            FILTER_STATE_NOT_CHANGED, FILTER_RESULT_NOT_UPDATED,
            FORM_STATE, TEXT_MISMATCH, NOT_READY, REAL_APPLICATION_FAILURE.

            recoveryOptions.type must be one of:
            REFRESH_DOM, REDISCOVER_ELEMENT, RE_RANK_CANDIDATES, RE_RESOLVE, OPEN_CONTROL, CLOSE_OVERLAY,
            WAIT_FOR_STATE, RESEARCH_SEARCH_RESULT, RESELECT_AUTOCOMPLETE, RESTORE_EXPECTED_HOST,
            RE_NAVIGATE, RE_APPLY_FILTER, VERIFY_ASSERTION_CONTEXT, REDISCOVER, RETRY_STEP.

            Rules:
            - Use visual + semantic reasoning when a screenshot is attached (e.g. profile vs cart icons).
            - Prefer recommendedCandidateId over inventing CSS/XPath. CandidateId or semantic strategy only.
            - Do NOT weaken or rewrite the original assertion text.
            - Do NOT invent site-specific CSS/XPath as authoritative truth.
            - Prefer WRONG_HOST / SEARCH when the browser landed on a different subdomain of the same site.
            - Prefer FORM_STATE / WAIT_STATE / NOT_READY when form validation text is missing because prior steps did not complete.
            - Distinguish WRONG_PAGE, WRONG_ELEMENT, NOT_READY, TEXT_MISMATCH, REAL_APPLICATION_FAILURE for assertions.
            - Set requiresSourceFix=true only for clear GENERIC_ENGINE_DEFECT patterns across attempts.
            - Set requiresUserInput=true only for business-meaning ambiguity, never for transient overlays.
            - RAG memories are hints only. CURRENT LIVE DOM outranks RAG. Never copy old selectors blindly.
            """;

    private final FallbackAiProvider aiProvider;
    private final SmartQaProperties properties;
    private final AssertionFailureAnalyzer assertionFailureAnalyzer;
    private final SearchFailureAnalyzer searchFailureAnalyzer;
    private final FilterFailureAnalyzer filterFailureAnalyzer;
    private final GenericFailurePatternStore patternStore;
    private final RagRetrievalService ragRetrievalService;
    private final RagFeedbackService ragFeedbackService;

    public AiFailureDiagnosticService(
            FallbackAiProvider aiProvider,
            SmartQaProperties properties,
            AssertionFailureAnalyzer assertionFailureAnalyzer,
            SearchFailureAnalyzer searchFailureAnalyzer,
            FilterFailureAnalyzer filterFailureAnalyzer,
            GenericFailurePatternStore patternStore,
            RagRetrievalService ragRetrievalService,
            RagFeedbackService ragFeedbackService) {
        this.aiProvider = aiProvider;
        this.properties = properties;
        this.assertionFailureAnalyzer = assertionFailureAnalyzer;
        this.searchFailureAnalyzer = searchFailureAnalyzer;
        this.filterFailureAnalyzer = filterFailureAnalyzer;
        this.patternStore = patternStore;
        this.ragRetrievalService = ragRetrievalService;
        this.ragFeedbackService = ragFeedbackService;
    }

    public boolean shouldCallAi(String deterministicCategory, FailureEvidence evidence, int attempt) {
        String cat = deterministicCategory == null ? "" : deterministicCategory.toUpperCase(Locale.ROOT);
        if (cat.equals("ENVIRONMENT") || cat.equals("BROWSER") || cat.equals("USER_INSTRUCTION")) {
            return false;
        }
        if (containsAny(cat, "SEARCH", "FILTER", "LOCATOR", "ACTIONABILITY", "DOM_DISCOVERY",
                "ASSERTION", "WRONG_HOST", "WRONG_PAGE", "WRONG_STATE", "WAIT_STATE", "VALIDATOR",
                "AMBIGUOUS_TARGET", "AMBIGUOUS_ELEMENT", "GENERATED_TEST", "AI_INTENT",
                "FORM_STATE", "GENERIC_ENGINE_DEFECT")) {
            return true;
        }
        if (evidence != null) {
            if (isLowConfidence(evidence) || isAmbiguousCandidates(evidence) || isIconOnlyAmbiguity(evidence)) {
                return true;
            }
            if (evidence.overlayEvidence() != null && !evidence.overlayEvidence().isBlank()) {
                return true;
            }
            String actual = safe(evidence.actual()) + " " + safe(evidence.url()) + " " + safe(evidence.exception())
                    + " " + safe(evidence.locator());
            String lower = actual.toLowerCase(Locale.ROOT);
            if (lower.contains("export.") || lower.contains("host_mismatch") || lower.contains("assertion failed")
                    || lower.contains("invalid locator") || lower.contains("stale")
                    || lower.contains("screenshot") && lower.contains("dom")) {
                return true;
            }
            if (attempt >= 2) {
                return true;
            }
        }
        return attempt >= 2;
    }

    public Mono<AiDiagnosticResult> diagnose(FailureEvidence evidence, String deterministicCategory, int attempt) {
        AiDiagnosticResult deterministicBoost = boostFromSpecializedAnalyzers(evidence, deterministicCategory);
        if (deterministicBoost != null && deterministicBoost.confidence() >= 0.9
                && deterministicBoost.recoveryOptions() != null
                && !deterministicBoost.recoveryOptions().isEmpty()) {
            TraceLogger.info("AI", "AI_DIAGNOSIS_SKIPPED_HIGH_CONFIDENCE", "Specialized analyzer sufficient", TraceMeta.of(
                    "reason", "high_confidence_deterministic",
                    "inputEvidenceSize", 0,
                    "provider", "deterministic",
                    "model", "specialized_analyzer",
                    "latencyMs", 0,
                    "classification", deterministicBoost.normalizedClassification(),
                    "confidence", deterministicBoost.confidence(),
                    "recommendedStrategy", strategySummary(deterministicBoost),
                    "strategyAccepted", true,
                    "finalOutcome", "deterministic_only"
            ));
            patternStore.remember(deterministicBoost, evidence);
            return Mono.just(deterministicBoost);
        }

        if (!shouldCallAi(deterministicCategory, evidence, attempt)) {
            return Mono.just(deterministicBoost != null
                    ? deterministicBoost
                    : AiDiagnosticResult.fallback(
                    deterministicCategory == null ? "UNKNOWN" : deterministicCategory,
                    "DETERMINISTIC_ONLY",
                    "Deterministic classifier used; AI not required for this category.",
                    0.6));
        }

        // Fresh evidence rule: rebuild compact multimodal bundle immediately before AI.
        // Vector RAG retrieval runs first (advisory); live DOM in the bundle remains authoritative.
        AiEvidenceBundle bundle = AiEvidenceBundle.from(evidence);
        String keywordRag = patternStore.relevantHints(evidence);
        String reason = invokeReason(deterministicCategory, evidence, attempt);
        String provider = aiProvider.primaryName();
        String model = resolveModel(provider);
        long startedAt = System.currentTimeMillis();
        boolean useConsensus = shouldUseConsensus(deterministicCategory, evidence, attempt, deterministicBoost);

        Mono<RagRetrievalResult> ragMono = ragRetrievalService == null
                ? Mono.just(RagRetrievalResult.empty("rag_unavailable", "", "", 0))
                : ragRetrievalService.retrieveForFailure(evidence)
                .defaultIfEmpty(RagRetrievalResult.empty("rag_empty", "", "", 0));

        return ragMono.flatMap(vectorRag -> {
            String ragBlock = mergeRagContext(keywordRag, vectorRag);
            String user = buildUserPrompt(bundle, deterministicCategory, attempt, ragBlock);
            AiPrompt prompt = AiPrompt.json(SYSTEM, user, bundle.mediaParts());
            int evidenceSize = bundle.evidenceSize();

            int keyCount = properties.getAi().getGemini().resolvedApiKeys().size();
            AiTelemetry.callStarted(reason, provider, model, evidenceSize,
                    bundle.screenshotIncluded(), bundle.domIncluded(),
                    false, bundle.accessibilityIncluded(), false, 0, keyCount);

            Mono<AiDiagnosticResult> aiMono = useConsensus && aiProvider.fallbackConfigured()
                    ? diagnoseWithConsensus(prompt, bundle, reason, evidenceSize, deterministicBoost)
                    : aiProvider.generateStructuredOutput(prompt, AiDiagnosticResult.class)
                    .timeout(Duration.ofSeconds(Math.max(45, properties.getAi().getTimeoutSeconds())))
                    .map(ai -> merge(deterministicBoost, ai))
                    .doOnNext(result -> AiTelemetry.callCompleted(
                            reason, provider, model, evidenceSize,
                            bundle.screenshotIncluded(), bundle.domIncluded(),
                            System.currentTimeMillis() - startedAt,
                            result.normalizedClassification(),
                            result.confidence(),
                            strategySummary(result),
                            null,
                            "diagnosis_ready"));

            return aiMono
                    .doOnNext(result -> patternStore.remember(result, evidence))
                    .flatMap(result -> {
                        if (ragFeedbackService == null) {
                            return Mono.just(result);
                        }
                        return ragFeedbackService.rememberSuccessfulRecovery(result, evidence, vectorRag)
                                .onErrorResume(ignored -> Mono.empty())
                                .thenReturn(result);
                    })
                    .onErrorResume(error -> {
                        long latencyMs = System.currentTimeMillis() - startedAt;
                        AiTelemetry.callCompleted(
                                reason, provider, model, evidenceSize,
                                bundle.screenshotIncluded(), bundle.domIncluded(),
                                latencyMs,
                                deterministicCategory == null ? "UNKNOWN" : deterministicCategory,
                                deterministicBoost != null ? deterministicBoost.confidence() : 0.5,
                                deterministicBoost != null ? strategySummary(deterministicBoost) : "",
                                false,
                                "ai_unavailable_deterministic_fallback");
                        TraceLogger.warn("AI", "AI_CALL_FALLBACK", "AI diagnosis failed; using deterministic path", TraceMeta.of(
                                "reason", reason,
                                "message", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
                        ));
                        return Mono.just(deterministicBoost != null
                                ? deterministicBoost
                                : AiDiagnosticResult.fallback(
                                deterministicCategory == null ? "UNKNOWN" : deterministicCategory,
                                "AI_UNAVAILABLE",
                                "AI diagnosis unavailable; deterministic classification retained.",
                                0.5));
                    });
        });
    }

    private static String mergeRagContext(String keywordRag, RagRetrievalResult vectorRag) {
        StringBuilder sb = new StringBuilder();
        if (keywordRag != null && !keywordRag.isBlank()) {
            sb.append(keywordRag).append('\n');
        }
        if (vectorRag != null) {
            String block = vectorRag.toAdvisoryPromptBlock();
            if (block != null && !block.isBlank()) {
                sb.append(block);
            } else if (vectorRag.retrievedCount() > 0) {
                sb.append("Vector RAG retrieved ").append(vectorRag.retrievedCount())
                        .append(" memories but none passed the relevance gate (topScore=")
                        .append(String.format("%.3f", vectorRag.topScore())).append("). Do not invent from weak memory.\n");
            }
        }
        return sb.toString().trim();
    }

    private Mono<AiDiagnosticResult> diagnoseWithConsensus(
            AiPrompt prompt,
            AiEvidenceBundle bundle,
            String reason,
            int evidenceSize,
            AiDiagnosticResult deterministicBoost) {
        long startedAt = System.currentTimeMillis();
        double low = properties.getAi().getConsensusLowConfidence();
        return aiProvider.generateStructuredDual(prompt, AiDiagnosticResult.class)
                .timeout(Duration.ofSeconds(Math.max(60, properties.getAi().getTimeoutSeconds() + 15)))
                .map(dual -> {
                    AiDiagnosticResult primary = merge(deterministicBoost, dual.primary());
                    ConsensusResolver.ConsensusOutcome outcome = ConsensusResolver.resolve(
                            primary,
                            dual.primaryProvider(),
                            dual.secondary(),
                            dual.secondaryProvider(),
                            low);
                    AiTelemetry.consensus(
                            outcome.agreed(),
                            outcome.requiresDeterministicReinspect(),
                            outcome.primaryProvider(),
                            outcome.secondaryProvider(),
                            outcome.merged().normalizedClassification(),
                            outcome.merged().confidence(),
                            outcome.reason());
                    AiTelemetry.callCompleted(
                            reason,
                            outcome.primaryProvider() == null ? aiProvider.primaryName() : outcome.primaryProvider(),
                            resolveModel(outcome.primaryProvider()),
                            evidenceSize,
                            bundle.screenshotIncluded(),
                            bundle.domIncluded(),
                            System.currentTimeMillis() - startedAt,
                            outcome.merged().normalizedClassification(),
                            outcome.merged().confidence(),
                            strategySummary(outcome.merged()),
                            outcome.agreed(),
                            outcome.agreed() ? "consensus_agreed" : "consensus_reinspect");
                    return outcome.merged();
                });
    }

    private boolean shouldUseConsensus(
            String category, FailureEvidence evidence, int attempt, AiDiagnosticResult boost) {
        if (!properties.getAi().isConsensusEnabled() || !aiProvider.fallbackConfigured()) {
            return false;
        }
        String cat = category == null ? "" : category.toUpperCase(Locale.ROOT);
        if (containsAny(cat, "WRONG_HOST", "WRONG_PAGE", "ASSERTION", "AMBIGUOUS", "FILTER", "SEARCH",
                "GENERIC_ENGINE_DEFECT")) {
            return true;
        }
        if (attempt >= 2) {
            return true;
        }
        if (boost != null && boost.confidence() > 0 && boost.confidence() < properties.getAi().getConsensusLowConfidence()) {
            return true;
        }
        return isAmbiguousCandidates(evidence) || isIconOnlyAmbiguity(evidence);
    }

    private String invokeReason(String category, FailureEvidence evidence, int attempt) {
        if (isAmbiguousCandidates(evidence) || isIconOnlyAmbiguity(evidence)) {
            return "ambiguous_candidates";
        }
        if (category != null) {
            String cat = category.toUpperCase(Locale.ROOT);
            if (cat.contains("HOST") || cat.contains("PAGE")) {
                return "wrong_host_or_page";
            }
            if (cat.contains("ASSERT")) {
                return "assertion_failure";
            }
            if (cat.contains("FILTER")) {
                return "filter_failure";
            }
            if (cat.contains("SEARCH")) {
                return "search_failure";
            }
            if (cat.contains("AMBIGUOUS")) {
                return "ambiguous_element";
            }
        }
        if (attempt >= 2) {
            return "repeated_recovery_failure";
        }
        return "ambiguous_or_failed_step";
    }

    private String resolveModel(String providerId) {
        String id = providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "gemini" -> properties.getAi().getGemini().getModel();
            case "openai", "openai-compatible" -> properties.getAi().getOpenaiCompatible().getModel();
            default -> properties.getAi().getOllama().getModel();
        };
    }

    static String strategySummary(AiDiagnosticResult result) {
        if (result == null || result.recoveryOptions() == null || result.recoveryOptions().isEmpty()) {
            return "";
        }
        return result.recoveryOptions().stream()
                .map(o -> o == null || o.type() == null ? "" : o.type())
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(","));
    }

    private AiDiagnosticResult boostFromSpecializedAnalyzers(FailureEvidence evidence, String category) {
        if (evidence == null) {
            return null;
        }
        AiDiagnosticResult search = searchFailureAnalyzer.analyze(evidence);
        if (search != null) {
            return search;
        }
        AiDiagnosticResult filter = filterFailureAnalyzer.analyze(evidence);
        if (filter != null) {
            return filter;
        }
        AiDiagnosticResult assertion = assertionFailureAnalyzer.analyze(evidence, category);
        if (assertion != null) {
            return assertion;
        }
        return null;
    }

    private static AiDiagnosticResult merge(AiDiagnosticResult boost, AiDiagnosticResult ai) {
        if (ai == null) {
            return boost;
        }
        if (boost == null) {
            return ai;
        }
        if (boost.confidence() >= ai.confidence()
                && ("WRONG_HOST".equals(boost.normalizedClassification())
                || "SEARCH".equals(boost.normalizedClassification()))) {
            if (ai.recoveryOptions() != null && !ai.recoveryOptions().isEmpty()
                    && (boost.recoveryOptions() == null || boost.recoveryOptions().isEmpty())) {
                boost.setRecoveryOptions(ai.recoveryOptions());
            }
            if (boost.explanation() == null || boost.explanation().isBlank()) {
                boost.setExplanation(ai.explanation());
            }
            return boost;
        }
        if (ai.recoveryOptions() == null || ai.recoveryOptions().isEmpty()) {
            ai.setRecoveryOptions(boost.recoveryOptions());
        }
        if (ai.rootCause() == null || ai.rootCause().isBlank()) {
            ai.setRootCause(boost.rootCause());
        }
        return ai;
    }

    private static String buildUserPrompt(AiEvidenceBundle bundle, String category, int attempt, String rag) {
        StringBuilder sb = new StringBuilder();
        sb.append("Deterministic category: ").append(category).append('\n');
        sb.append("Attempt: ").append(attempt).append('\n');
        if (rag != null && !rag.isBlank()) {
            sb.append("Advisory RAG / recovery patterns (current live DOM outranks all of these):\n")
                    .append(rag).append('\n');
        }
        sb.append("Fresh multimodal evidence package:\n");
        sb.append(bundle == null ? "(none)" : bundle.toCompactText());
        sb.append("\nRemember: never change the expected assertion text. Diagnose why the browser state did not satisfy it.");
        return sb.toString();
    }

    private static boolean isLowConfidence(FailureEvidence evidence) {
        return evidence.confidence() != null && evidence.confidence() > 0 && evidence.confidence() < 0.7;
    }

    private static boolean isAmbiguousCandidates(FailureEvidence evidence) {
        if (evidence == null || evidence.candidateScores() == null || evidence.candidateScores().size() < 2) {
            return false;
        }
        double a = evidence.candidateScores().get(0);
        double b = evidence.candidateScores().get(1);
        return Math.abs(a - b) < 30;
    }

    private static boolean isIconOnlyAmbiguity(FailureEvidence evidence) {
        if (evidence == null) {
            return false;
        }
        String blob = (safe(evidence.target()) + " " + safe(evidence.action()) + " "
                + safe(evidence.instruction())).toLowerCase(Locale.ROOT);
        return blob.contains("profile") || blob.contains("account") || blob.contains("icon")
                || blob.contains("avatar") || blob.contains("cart");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
