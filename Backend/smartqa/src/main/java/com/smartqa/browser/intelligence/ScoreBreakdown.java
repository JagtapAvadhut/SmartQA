package com.smartqa.browser.intelligence;

/**
 * Explainable breakdown of {@link LocatorRanker} — not a second ranker.
 * {@link #total} is the score actually used for ranking.
 */
public record ScoreBreakdown(
        double semantic,
        double context,
        double role,
        double ownership,
        double visibility,
        double actionability,
        double layout,
        double accessibility,
        double history,
        double visual,
        double frame,
        double shadow,
        double assertionRelevance,
        double total,
        String explanation,
        double textExactScore,
        double textSimilarityScore,
        double accessibleNameScore,
        double capabilityScore,
        double controlTypeScore,
        double containerScore,
        double entityScore,
        double optionValueScore,
        double rangeBoundScore,
        double activeScopeScore,
        double stateScore,
        double relationshipScore,
        String hardConstraint
) {
    public ScoreBreakdown(
            double semantic, double context, double role, double ownership, double visibility,
            double actionability, double layout, double accessibility, double history, double visual,
            double frame, double shadow, double assertionRelevance, double total, String explanation
    ) {
        this(semantic, context, role, ownership, visibility, actionability, layout, accessibility, history,
                visual, frame, shadow, assertionRelevance, total, explanation,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }

    public static ScoreBreakdown rejected(HardConstraint constraint) {
        String name = constraint == null ? "REJECTED" : constraint.name();
        return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "hard reject: " + name,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, name);
    }

    public double semanticScore() {
        return semantic;
    }

    public double ownershipScore() {
        return ownership;
    }

    public double roleScore() {
        return role;
    }

    public double visualScore() {
        return visual;
    }

    public double actionabilityScore() {
        return actionability;
    }

    public double historyScore() {
        return history;
    }

    public double relationshipScoreOrZero() {
        return relationshipScore;
    }

    public String whySelected() {
        return explanation == null ? "" : explanation;
    }
}
