package com.smartqa.browser.intelligence;

/**
 * Consensus across deterministic evidence channels.
 * AI may only add a small weight and cannot override strong contradictory DOM ownership.
 */
public final class EvidenceConsensusEngine {

    public record Scores(
            double semantic,
            double accessibility,
            double parentContext,
            double actionability,
            double visual,
            double location,
            double ai
    ) {
    }

    public record Decision(
            double finalScore,
            boolean aiAccepted,
            String reason
    ) {
    }

    private EvidenceConsensusEngine() {
    }

    public static Decision decide(Scores scores, boolean strongDomOwnershipContradiction) {
        double weighted = scores.semantic() * 0.30
                + scores.accessibility() * 0.20
                + scores.parentContext() * 0.15
                + scores.actionability() * 0.15
                + scores.visual() * 0.10
                + scores.location() * 0.05
                + scores.ai() * 0.05;
        if (strongDomOwnershipContradiction && scores.ai() > 0) {
            return new Decision(weighted - scores.ai() * 0.05, false,
                    "AI recommendation rejected: contradicts strong DOM ownership evidence");
        }
        boolean aiAccepted = scores.ai() > 0 && !strongDomOwnershipContradiction;
        return new Decision(weighted, aiAccepted,
                aiAccepted ? "AI score applied within consensus weights" : "Deterministic consensus only");
    }
}
