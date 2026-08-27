package com.smartqa.intent;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.springframework.stereotype.Component;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class IntentValidator {
    private static final Set<String> SUPPORTED_FILTER_OPERATORS = Set.of(
            "equals", "not_equals", "contains", "not_contains", "starts_with", "ends_with",
            "greater_than", "greater_than_or_equal", "less_than", "less_than_or_equal",
            "between", "in", "not_in", "exists", "not_exists"
    );
    private static final Pattern LOCATOR_SHAPED = Pattern.compile(
            "(?i)(xpath=|css=|//|\\[\\s*@|nth-child\\(|nth\\(|page\\.locator|getByRole|getByTestId|"
                    + "document\\.querySelector|=>\\s*\\{|data-testid=|coordinates?\\s*[:=]|click\\(\\s*\\d+)"
    );

    public IntentContract validate(IntentContract contract) {
        if (contract == null) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Intent contract is missing");
        }
        String status = contract.status() == null ? "" : contract.status().trim().toUpperCase(Locale.ROOT);
        if (!IntentContract.READY.equals(status) && !IntentContract.NEEDS_CLARIFICATION.equals(status)) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Intent status must be READY or NEEDS_CLARIFICATION");
        }
        List<ClarificationQuestion> clarifications = contract.clarifications() == null
                ? List.of()
                : contract.clarifications();
        if (IntentContract.NEEDS_CLARIFICATION.equals(status)) {
            if (clarifications.isEmpty()) {
                throw new SmartQaException(
                        ErrorCode.INTENT_INVALID,
                        "Clarification status requires at least one question");
            }
            return new IntentContract(
                    IntentContract.NEEDS_CLARIFICATION,
                    contract.testName(),
                    contract.confidence(),
                    contract.scenarios() == null ? List.of() : contract.scenarios(),
                    clarifications
            );
        }
        if (contract.scenarios() == null || contract.scenarios().isEmpty()) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Intent must include at least one scenario");
        }
        contract = IntentIdUniquifier.uniquify(contract);
        Set<String> ids = new HashSet<>();
        List<IntentScenario> cleanedScenarios = new ArrayList<>();
        for (IntentScenario scenario : contract.scenarios()) {
            if (scenario == null || isBlank(scenario.name())) {
                throw new SmartQaException(ErrorCode.INTENT_INVALID, "Each scenario needs a name");
            }
            if (scenario.steps() == null || scenario.steps().isEmpty()) {
                throw new SmartQaException(ErrorCode.INTENT_INVALID, "Scenario '" + scenario.name() + "' has no steps");
            }
            List<IntentStep> cleanedSteps = new ArrayList<>();
            for (IntentStep step : scenario.steps()) {
                if (isUnnecessaryWait(step)) {
                    TraceLogger.warn("INTENT", "AI_STEP_REMOVED",
                            "Removed AI-hallucinated wait step with no value",
                            TraceMeta.of("stepId", step.id(), "action", step.action()));
                    continue;
                }
                IntentStep normalized = UrlNavigationNormalizer.rewrite(step);
                if (normalized.scenarioId() == null || normalized.scenarioId().isBlank()) {
                    normalized = normalized.withScenarioId(scenario.id());
                }
                validateStep(normalized, ids);
                cleanedSteps.add(normalized);
            }
            if (cleanedSteps.isEmpty()) {
                throw new SmartQaException(ErrorCode.INTENT_INVALID, "Scenario '" + scenario.name() + "' has no valid steps after normalization");
            }
            IntentPlanDag.validate(cleanedSteps);
            cleanedScenarios.add(new IntentScenario(scenario.id(), scenario.name(), cleanedSteps));
        }
        double confidence = contract.confidence() == null ? 0.8 : contract.confidence();
        if (confidence < 0.45) {
            return new IntentContract(
                    IntentContract.NEEDS_CLARIFICATION,
                    contract.testName(),
                    confidence,
                    cleanedScenarios,
                    List.of(new ClarificationQuestion(
                            "low_confidence",
                            "I am not confident about this test. Please confirm the intended flow or add missing details.",
                            List.of("Proceed as interpreted", "I will edit the natural-language steps")
                    ))
            );
        }
        return new IntentContract(
                IntentContract.READY,
                contract.testName(),
                confidence,
                cleanedScenarios,
                clarifications
        );
    }

    private void validateStep(IntentStep step, Set<String> ids) {
        if (step == null || isBlank(step.id())) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Each step needs an id");
        }
        if (!ids.add(step.id())) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Duplicate step id: " + step.id());
        }
        if (isBlank(step.action())) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Step " + step.id() + " is missing an action");
        }
        String action = SupportedActions.canonicalize(step.action().trim().toLowerCase(Locale.ROOT));
        if (!SupportedActions.ALL.contains(action)) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Unsupported action: " + step.action());
        }
        switch (action) {
            case SupportedActions.NAVIGATE -> {
                if (isBlank(step.target()) && isBlank(step.value())) {
                    throw new SmartQaException(ErrorCode.INTENT_INVALID, "Navigate step needs a URL or site name");
                }
            }
            case SupportedActions.CLICK, SupportedActions.HOVER, SupportedActions.CHECKBOX, SupportedActions.RADIO,
                 SupportedActions.EXPAND, SupportedActions.COLLAPSE, SupportedActions.ADD_TO_CART,
                 SupportedActions.QUANTITY, SupportedActions.SUBMIT, SupportedActions.VISUAL_TARGET,
                 SupportedActions.CLEAR_FILTERS, SupportedActions.WAIT_FOR_STATE -> {
                if (isBlank(step.target())) {
                    throw new SmartQaException(ErrorCode.INTENT_INVALID, action + " step needs a target");
                }
            }
            case SupportedActions.INPUT, SupportedActions.SELECT, SupportedActions.SEARCH, SupportedActions.SET_VALUE -> {
                if (isBlank(step.target()) || isBlank(step.value())) {
                    throw new SmartQaException(ErrorCode.INTENT_INVALID, action + " step needs a target and value");
                }
            }
            case SupportedActions.PRESS_KEY, SupportedActions.WAIT -> {
                if (isBlank(step.value())) {
                    throw new SmartQaException(ErrorCode.INTENT_INVALID, action + " step needs a value");
                }
            }
            case SupportedActions.SCROLL -> {
                // target optional — "down" / "up" allowed via value
            }
            case SupportedActions.VERIFY -> {
                if (isBlank(step.target()) && isBlank(step.value()) && isBlank(step.assertion())) {
                    throw new SmartQaException(ErrorCode.INTENT_INVALID, "Verify step needs a target, value, or assertion");
                }
            }
            case SupportedActions.FILTER -> {
                if ((step.filter() == null || isBlank(step.filter().field())) && isBlank(step.target())) {
                    throw new SmartQaException(ErrorCode.INTENT_INVALID, "Filter step needs a field or target");
                }
                validateFilter(step.filter(), step.id());
            }
            default -> {
            }
        }
        if (!isBlank(step.location())) {
            String normalized = LocationHint.normalize(step.location());
            if (!LocationHint.ALL.contains(normalized) && !LocationHint.AUTO.equals(normalized)) {
                throw new SmartQaException(ErrorCode.INTENT_INVALID, "Unsupported location hint: " + step.location());
            }
        }
        rejectLocatorLikeText(step);
    }

    private static void validateFilter(IntentFilter filter, String stepId) {
        if (filter == null) {
            return;
        }
        if (isBlank(filter.operator())) {
            throw new SmartQaException(ErrorCode.INTENT_VALIDATION_ERROR, "Filter operator is required for step " + stepId);
        }
        String op = filter.operator().trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FILTER_OPERATORS.contains(op)) {
            throw new SmartQaException(ErrorCode.INTENT_VALIDATION_ERROR, "Unsupported filter operator '" + filter.operator() + "' for step " + stepId);
        }
        if ("between".equals(op) && (filter.min() == null || filter.max() == null)) {
            throw new SmartQaException(ErrorCode.INTENT_VALIDATION_ERROR, "Between filter requires min and max for step " + stepId);
        }
    }

    private static void rejectLocatorLikeText(IntentStep step) {
        boolean allowUrl = SupportedActions.NAVIGATE.equalsIgnoreCase(step.action());
        if ((hasLocatorLikeText(step.target()) && !(allowUrl && looksLikeUrl(step.target())))
                || (hasLocatorLikeText(step.value()) && !(allowUrl && looksLikeUrl(step.value())))
                || hasLocatorLikeText(step.assertion())) {
            throw new SmartQaException(ErrorCode.INTENT_VALIDATION_ERROR,
                    "Intent step " + step.id() + " contains locator-like implementation details. Provide semantic instructions only.");
        }
    }

    private static boolean hasLocatorLikeText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (UrlNavigationNormalizer.looksLikeHttpUrl(value)) {
            return false;
        }
        return LOCATOR_SHAPED.matcher(value).find();
    }

    private static boolean looksLikeUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isUnnecessaryWait(IntentStep step) {
        if (step == null || isBlank(step.action())) return false;
        String action = step.action().trim().toLowerCase(Locale.ROOT);
        return SupportedActions.WAIT.equals(action) && isBlank(step.value());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
