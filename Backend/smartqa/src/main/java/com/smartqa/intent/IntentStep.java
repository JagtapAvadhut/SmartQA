package com.smartqa.intent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Canonical semantic step. {@code id} is the backend-authoritative stepId.
 * Implementation locators (CSS, XPath, Playwright calls, coordinates) must not appear here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentStep(
        String id,
        String action,
        String target,
        String value,
        String assertion,
        IntentFilter filter,
        String location,
        String scenarioId,
        String targetType,
        String controlType,
        String containerContext,
        List<String> dependsOn,
        List<String> preconditions,
        String expectedState,
        List<String> postconditions,
        String timeoutPolicy,
        String recoveryPolicy,
        List<String> semanticConstraints
) {
    public IntentStep {
        dependsOn = copyList(dependsOn);
        preconditions = copyList(preconditions);
        postconditions = copyList(postconditions);
        semanticConstraints = copyList(semanticConstraints);
    }

    public IntentStep(String id, String action, String target, String value, String assertion) {
        this(id, action, target, value, assertion, null, null);
    }

    public IntentStep(String id, String action, String target, String value, String assertion, IntentFilter filter) {
        this(id, action, target, value, assertion, filter, null);
    }

    public IntentStep(
            String id,
            String action,
            String target,
            String value,
            String assertion,
            IntentFilter filter,
            String location
    ) {
        this(
                id, action, target, value, assertion, filter, location,
                null, null, null, null,
                List.of(), List.of(), null, List.of(),
                null, null, List.of()
        );
    }

    public String stepId() {
        return id;
    }

    @JsonIgnore
    public String filterField() {
        return filter == null ? null : filter.field();
    }

    @JsonIgnore
    public String filterValue() {
        return filter == null ? null : filter.displayValue();
    }

    @JsonIgnore
    public String timeout() {
        return timeoutPolicy;
    }

    public IntentStep withId(String newId) {
        return new IntentStep(
                newId, action, target, value, assertion, filter, location,
                scenarioId, targetType, controlType, containerContext,
                dependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, semanticConstraints
        );
    }

    public IntentStep withScenarioId(String newScenarioId) {
        return new IntentStep(
                id, action, target, value, assertion, filter, location,
                newScenarioId, targetType, controlType, containerContext,
                dependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, semanticConstraints
        );
    }

    public IntentStep withSemantic(String newControlType, String newTargetType) {
        return new IntentStep(
                id, action, target, value, assertion, filter, location,
                scenarioId, newTargetType, newControlType, containerContext,
                dependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, semanticConstraints
        );
    }

    public IntentStep withActionTargetValue(String newAction, String newTarget, String newValue) {
        return new IntentStep(
                id, newAction, newTarget, newValue, assertion, filter, location,
                scenarioId, targetType, controlType, containerContext,
                dependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, semanticConstraints
        );
    }

    public IntentStep withTargetValueAssertion(String newTarget, String newValue, String newAssertion) {
        return new IntentStep(
                id, action, newTarget, newValue, newAssertion, filter, location,
                scenarioId, targetType, controlType, containerContext,
                dependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, semanticConstraints
        );
    }

    public IntentStep withContainerContext(String newContainerContext) {
        return new IntentStep(
                id, action, target, value, assertion, filter, location,
                scenarioId, targetType, controlType, newContainerContext,
                dependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, semanticConstraints
        );
    }

    public IntentStep withDependsOn(List<String> newDependsOn) {
        return new IntentStep(
                id, action, target, value, assertion, filter, location,
                scenarioId, targetType, controlType, containerContext,
                newDependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, semanticConstraints
        );
    }

    public IntentStep withSemanticConstraints(List<String> newSemanticConstraints) {
        return new IntentStep(
                id, action, target, value, assertion, filter, location,
                scenarioId, targetType, controlType, containerContext,
                dependsOn, preconditions, expectedState, postconditions,
                timeoutPolicy, recoveryPolicy, newSemanticConstraints
        );
    }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }
}
