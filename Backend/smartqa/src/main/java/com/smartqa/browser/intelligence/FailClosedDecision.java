package com.smartqa.browser.intelligence;

import java.util.List;

/**
 * Fail-closed resolution outcomes. Soft scores cannot override these.
 */
public final class FailClosedDecision {

    public enum Outcome {
        UNIQUE,
        INCOMPLETE_CAPTURE,
        NOT_CONFIDENT,
        AMBIGUOUS,
        NOT_FOUND
    }

    public record Result(Outcome outcome, String reason) {
        public boolean proceed() {
            return outcome == Outcome.UNIQUE;
        }
    }

    private FailClosedDecision() {
    }

    public static Result evaluate(List<LocatorRanker.RankedElement> ranked, boolean captureComplete) {
        if (!captureComplete) {
            return new Result(Outcome.INCOMPLETE_CAPTURE, "Browser evidence is incomplete");
        }
        if (ranked == null || ranked.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, "No live candidate survived hard constraints");
        }
        LocatorRanker.RankedElement top = ranked.getFirst();
        if (LocatorRanker.confidence(top.score()) < 0.70 && top.score() < 100) {
            return new Result(Outcome.NOT_CONFIDENT, "Top candidate confidence is insufficient");
        }
        if (ranked.size() == 1) {
            return new Result(Outcome.UNIQUE, "Single surviving candidate");
        }
        if (LocatorRanker.uniqueWinner(ranked)) {
            return new Result(Outcome.UNIQUE, "Unique winner after margin check");
        }
        return new Result(Outcome.AMBIGUOUS, "Multiple live candidates remain inside the ambiguity margin");
    }

    /**
     * Two or more candidates are equally supported for the same semantic target.
     * The engine must pause rather than click the first match.
     */
    public static boolean equallySupportedDuplicates(List<LocatorRanker.RankedElement> ranked, String target) {
        if (evaluate(ranked, true).outcome() != Outcome.AMBIGUOUS) {
            return false;
        }
        LocatorRanker.RankedElement first = ranked.getFirst();
        LocatorRanker.RankedElement second = ranked.get(1);
        if (first.score() < 80 || second.score() < 80) {
            return false;
        }
        String hint = normalize(target);
        String left = normalize(label(first));
        String right = normalize(label(second));
        if (hint.isBlank()) {
            return left.equals(right) && !left.isBlank();
        }
        return left.contains(hint) && right.contains(hint);
    }

    private static String label(LocatorRanker.RankedElement ranked) {
        if (ranked == null || ranked.element() == null) {
            return "";
        }
        var element = ranked.element();
        if (element.accessibleName() != null && !element.accessibleName().isBlank()) {
            return element.accessibleName();
        }
        if (element.text() != null && !element.text().isBlank()) {
            return element.text();
        }
        return element.label() == null ? "" : element.label();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
