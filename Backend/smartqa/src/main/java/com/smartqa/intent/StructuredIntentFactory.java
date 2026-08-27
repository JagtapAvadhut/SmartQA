package com.smartqa.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds a READY IntentContract from structured UI steps without calling an LLM.
 */
public final class StructuredIntentFactory {

    private StructuredIntentFactory() {
    }

    public record StructuredStepInput(
            String id,
            String action,
            String target,
            String value,
            String assertion,
            String location,
            IntentFilter filter
    ) {
    }

    public static IntentContract fromSteps(String testName, String applicationUrl, List<StructuredStepInput> steps) {
        List<IntentStep> intentSteps = new ArrayList<>();
        int order = 1;
        boolean hasNavigate = false;
        if (steps != null) {
            for (StructuredStepInput step : steps) {
                if (step == null || isBlank(step.action())) {
                    continue;
                }
                String action = step.action().trim().toLowerCase(Locale.ROOT);
                String id = isBlank(step.id()) ? "s1_step" + order : step.id().trim();
                String target = blankToNull(step.target());
                String value = blankToNull(step.value());
                String assertion = blankToNull(step.assertion());
                // VERIFY: UI often puts expected text in assertion; browser resolution needs target.
                if (SupportedActions.VERIFY.equals(action) && target == null && assertion != null) {
                    target = assertion;
                }
                IntentStep intentStep = UrlNavigationNormalizer.rewrite(new IntentStep(
                        id,
                        action,
                        target,
                        value,
                        assertion,
                        step.filter(),
                        LocationHint.normalize(step.location())
                ));
                if (SupportedActions.NAVIGATE.equalsIgnoreCase(intentStep.action())) {
                    hasNavigate = true;
                }
                intentSteps.add(intentStep);
                order++;
            }
        }
        if (!hasNavigate && !isBlank(applicationUrl)) {
            intentSteps.add(0, new IntentStep(
                    "s1_step0",
                    SupportedActions.NAVIGATE,
                    "application",
                    applicationUrl.trim(),
                    null,
                    null,
                    LocationHint.AUTO
            ));
            // renumber not required — ids already unique
        }
        if (intentSteps.isEmpty()) {
            throw new IllegalArgumentException("At least one structured step is required");
        }
        String name = isBlank(testName) ? "Structured test" : testName.trim();
        return IntentIdUniquifier.uniquify(new IntentContract(
                IntentContract.READY,
                name,
                0.95,
                List.of(new IntentScenario("s1", name, intentSteps)),
                List.of()
        ));
    }

    public static String toNaturalLanguage(List<StructuredStepInput> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int i = 1;
        for (StructuredStepInput step : steps) {
            if (step == null || isBlank(step.action())) {
                continue;
            }
            builder.append(i++).append(". ")
                    .append(step.action().trim().toUpperCase(Locale.ROOT));
            if (!isBlank(step.location()) && !LocationHint.AUTO.equalsIgnoreCase(step.location())) {
                builder.append(" [").append(LocationHint.normalize(step.location())).append(']');
            }
            if (!isBlank(step.target())) {
                builder.append(' ').append(step.target().trim());
            }
            if (!isBlank(step.value())) {
                builder.append(" = ").append(step.value().trim());
            }
            if (!isBlank(step.assertion())) {
                builder.append(" (").append(step.assertion().trim()).append(')');
            }
            if (step.filter() != null && !isBlank(step.filter().field())) {
                builder.append(" filter ").append(step.filter().field());
                if (!isBlank(step.filter().value())) {
                    builder.append('=').append(step.filter().value());
                }
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    public static String newStepId() {
        return "step_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
