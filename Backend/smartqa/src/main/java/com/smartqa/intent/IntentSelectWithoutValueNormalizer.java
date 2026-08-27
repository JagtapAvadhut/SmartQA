package com.smartqa.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SELECT without a value is not a dropdown choice — it is almost always
 * "open/expand this named section" (Brand, Color, Size). Coerce before validation
 * so informal instructions do not fail-fast.
 */
public final class IntentSelectWithoutValueNormalizer {

    private IntentSelectWithoutValueNormalizer() {
    }

    public static IntentContract normalize(IntentContract contract) {
        if (contract == null || contract.scenarios() == null) {
            return contract;
        }
        List<IntentScenario> scenarios = new ArrayList<>();
        boolean changed = false;
        for (IntentScenario scenario : contract.scenarios()) {
            if (scenario == null || scenario.steps() == null) {
                scenarios.add(scenario);
                continue;
            }
            List<IntentStep> steps = new ArrayList<>();
            for (IntentStep step : scenario.steps()) {
                IntentStep next = coerce(step);
                changed = changed || next != step;
                steps.add(next);
            }
            scenarios.add(new IntentScenario(scenario.id(), scenario.name(), steps));
        }
        if (!changed) {
            return contract;
        }
        return new IntentContract(
                contract.status(),
                contract.testName(),
                contract.confidence(),
                scenarios,
                contract.clarifications()
        );
    }

    private static IntentStep coerce(IntentStep step) {
        if (step == null) {
            return step;
        }
        String action = SupportedActions.canonicalize(step.action());
        if (!SupportedActions.SELECT.equals(action)) {
            return step;
        }
        if (step.value() != null && !step.value().isBlank()) {
            return step;
        }
        String target = stripOpenWords(step.target());
        if (target.isBlank()) {
            return step;
        }
        String nextAction = ControlPhrase.isFilterFieldToken(target) || looksLikeOpen(step)
                ? SupportedActions.EXPAND
                : SupportedActions.CLICK;
        return step.withActionTargetValue(nextAction, target, null);
    }

    private static boolean looksLikeOpen(IntentStep step) {
        String blob = ((step.target() == null ? "" : step.target()) + " " + (step.action() == null ? "" : step.action()))
                .toLowerCase(Locale.ROOT);
        return blob.contains("open") || blob.contains("expand");
    }

    private static String stripOpenWords(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("(?i)\\band\\s+open\\b", " ")
                .replaceAll("(?i)\\bopen\\b", " ")
                .replaceAll("(?i)\\bselect\\b", " ")
                .replaceAll("[.]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
