package com.smartqa.pipeline;

import java.util.Locale;
import java.util.Objects;

/**
 * Compares Gemini primary diagnosis with Ollama second opinion.
 * Agreement raises confidence; disagreement does NOT authorize blind execution —
 * forces another deterministic browser inspection (or user ask for business meaning).
 */
public final class ConsensusResolver {

    public static final double DEFAULT_LOW_CONFIDENCE = 0.65;

    private ConsensusResolver() {
    }

    public record ConsensusOutcome(
            AiDiagnosticResult merged,
            boolean agreed,
            boolean requiresDeterministicReinspect,
            boolean requiresUserInput,
            String primaryProvider,
            String secondaryProvider,
            String reason
    ) {
    }

    public static ConsensusOutcome resolve(
            AiDiagnosticResult primary,
            String primaryProvider,
            AiDiagnosticResult secondary,
            String secondaryProvider) {
        return resolve(primary, primaryProvider, secondary, secondaryProvider, DEFAULT_LOW_CONFIDENCE);
    }

    public static ConsensusOutcome resolve(
            AiDiagnosticResult primary,
            String primaryProvider,
            AiDiagnosticResult secondary,
            String secondaryProvider,
            double lowConfidenceThreshold) {
        if (primary == null && secondary == null) {
            AiDiagnosticResult empty = AiDiagnosticResult.fallback(
                    "UNKNOWN", "NO_AI", "No AI opinions available.", 0.4);
            return new ConsensusOutcome(empty, false, true, false, primaryProvider, secondaryProvider, "both_missing");
        }
        if (secondary == null) {
            return new ConsensusOutcome(primary, false, false, primary.requiresUserInput(),
                    primaryProvider, secondaryProvider, "secondary_unavailable");
        }
        if (primary == null) {
            return new ConsensusOutcome(secondary, false, false, secondary.requiresUserInput(),
                    primaryProvider, secondaryProvider, "primary_unavailable");
        }

        String pClass = primary.normalizedClassification();
        String sClass = secondary.normalizedClassification();
        String pStrategy = firstStrategy(primary);
        String sStrategy = firstStrategy(secondary);
        boolean classAgree = sameFamily(pClass, sClass);
        boolean strategyAgree = strategyCompatible(pStrategy, sStrategy);
        boolean agreed = classAgree && strategyAgree;

        AiDiagnosticResult merged = copyShallow(primary);
        if (agreed) {
            double boosted = Math.min(0.99, Math.max(primary.confidence(), secondary.confidence()) + 0.08);
            merged.setConfidence(boosted);
            if ((merged.explanation() == null || merged.explanation().isBlank())
                    && secondary.explanation() != null) {
                merged.setExplanation(secondary.explanation());
            }
            if ((merged.rootCause() == null || merged.rootCause().isBlank())
                    && secondary.rootCause() != null) {
                merged.setRootCause(secondary.rootCause());
            }
            return new ConsensusOutcome(merged, true, false,
                    primary.requiresUserInput() || secondary.requiresUserInput(),
                    primaryProvider, secondaryProvider, "agreement_boosted");
        }

        // Disagreement: do not blindly execute. Prefer safer / more specific classification.
        AiDiagnosticResult preferred = preferSafer(primary, secondary);
        preferred.setConfidence(Math.min(preferred.confidence(), lowConfidenceThreshold));
        boolean businessAmbiguity = preferred.requiresUserInput()
                || secondary.requiresUserInput()
                || "AMBIGUOUS_TARGET".equals(preferred.normalizedClassification())
                || "AMBIGUOUS_ELEMENT".equals(preferred.normalizedClassification());
        if (businessAmbiguity) {
            preferred.setRequiresUserInput(true);
            if (preferred.userQuestion() == null || preferred.userQuestion().isBlank()) {
                preferred.setUserQuestion(
                        "AI providers disagree on which UI meaning matches your instruction. Which target did you mean?");
            }
            return new ConsensusOutcome(preferred, false, true, true,
                    primaryProvider, secondaryProvider,
                    "disagreement_business_ambiguity:" + pClass + "!=" + sClass);
        }
        // Force deterministic re-inspect via REFRESH_DOM / REDISCOVER
        if (preferred.recoveryOptions() == null || preferred.recoveryOptions().isEmpty()) {
            RecoveryOption refresh = new RecoveryOption(
                    "REFRESH_DOM",
                    "Providers disagreed; re-inspect live DOM before recovery.",
                    true);
            refresh.setConfidence(0.55);
            preferred.setRecoveryOptions(java.util.List.of(refresh));
        }
        return new ConsensusOutcome(preferred, false, true, false,
                primaryProvider, secondaryProvider,
                "disagreement_reinspect:" + pClass + "/" + pStrategy + "!=" + sClass + "/" + sStrategy);
    }

    static boolean sameFamily(String a, String b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (aliases(a).equals(aliases(b))) {
            return true;
        }
        return (a.contains("HOST") && b.contains("HOST"))
                || (a.contains("SEARCH") && b.contains("SEARCH"))
                || (a.contains("FILTER") && b.contains("FILTER"))
                || (a.contains("ASSERT") && b.contains("ASSERT"))
                || (a.contains("AMBIGUOUS") && b.contains("AMBIGUOUS"))
                || (a.contains("WAIT") && b.contains("WAIT"));
    }

    private static String aliases(String c) {
        String n = c.toUpperCase(Locale.ROOT);
        return switch (n) {
            case "AMBIGUOUS_ELEMENT", "AMBIGUOUS_TARGET" -> "AMBIGUOUS";
            case "WRONG_HOST", "WRONG_PAGE" -> "WRONG_CONTEXT";
            case "FILTER_NOT_FOUND", "FILTER_NOT_OPEN", "FILTER_OPTION_NOT_FOUND",
                 "FILTER_NOT_SELECTED", "FILTER_STATE_NOT_CHANGED", "FILTER_RESULT_NOT_UPDATED" -> "FILTER";
            default -> n;
        };
    }

    static boolean strategyCompatible(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            return true;
        }
        if (a.equalsIgnoreCase(b)) {
            return true;
        }
        String na = a.toUpperCase(Locale.ROOT);
        String nb = b.toUpperCase(Locale.ROOT);
        if ((na.contains("REDISCOVER") || na.contains("RE_RESOLVE") || na.contains("RE_RANK"))
                && (nb.contains("REDISCOVER") || nb.contains("RE_RESOLVE") || nb.contains("RE_RANK"))) {
            return true;
        }
        if (na.contains("HOST") && nb.contains("HOST")) {
            return true;
        }
        if (na.contains("FILTER") && nb.contains("FILTER")) {
            return true;
        }
        return false;
    }

    private static AiDiagnosticResult preferSafer(AiDiagnosticResult a, AiDiagnosticResult b) {
        int scoreA = specificityScore(a);
        int scoreB = specificityScore(b);
        if (scoreB > scoreA) {
            return copyShallow(b);
        }
        if (scoreA > scoreB) {
            return copyShallow(a);
        }
        return a.confidence() >= b.confidence() ? copyShallow(a) : copyShallow(b);
    }

    private static int specificityScore(AiDiagnosticResult r) {
        if (r == null) {
            return 0;
        }
        String c = r.normalizedClassification();
        int score = 0;
        if (c.contains("HOST") || c.contains("WRONG_PAGE") || c.contains("SEARCH") || c.contains("FILTER")) {
            score += 3;
        }
        if (c.contains("AMBIGUOUS") || c.contains("ASSERTION")) {
            score += 2;
        }
        if (r.requiresSourceFix()) {
            score -= 1;
        }
        if (r.recoveryOptions() != null && !r.recoveryOptions().isEmpty()) {
            score += 1;
        }
        return score;
    }

    private static String firstStrategy(AiDiagnosticResult r) {
        if (r == null || r.recoveryOptions() == null || r.recoveryOptions().isEmpty()) {
            return "";
        }
        RecoveryOption opt = r.recoveryOptions().getFirst();
        return opt == null || opt.type() == null ? "" : opt.type().trim().toUpperCase(Locale.ROOT);
    }

    private static AiDiagnosticResult copyShallow(AiDiagnosticResult src) {
        if (src == null) {
            return null;
        }
        AiDiagnosticResult copy = new AiDiagnosticResult();
        copy.setClassification(src.classification());
        copy.setRootCause(src.rootCause());
        copy.setConfidence(src.confidence());
        copy.setExplanation(src.explanation());
        copy.setRecoveryOptions(src.recoveryOptions());
        copy.setRequiresUserInput(src.requiresUserInput());
        copy.setRequiresSourceFix(src.requiresSourceFix());
        copy.setUserQuestion(src.userQuestion());
        copy.setUserOptions(src.userOptions());
        copy.setAssertionSubCategory(src.assertionSubCategory());
        copy.setSearchSubCategory(src.searchSubCategory());
        copy.setFilterSubCategory(src.filterSubCategory());
        copy.setResponsibleSubsystem(src.responsibleSubsystem());
        copy.setRecommendedCandidateId(src.recommendedCandidateId());
        return copy;
    }
}
