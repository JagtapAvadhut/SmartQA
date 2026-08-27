package com.smartqa.browser;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartqa.intent.IntentFilter;
import com.smartqa.intent.IntentStep;

import java.util.List;
import java.util.UUID;

public record ExecutionPlan(
        UUID testCaseId,
        String testName,
        String baseUrl,
        List<PlannedStep> steps,
        UUID executionRunId
) {
    public ExecutionPlan(UUID testCaseId, String testName, String baseUrl, List<PlannedStep> steps) {
        this(testCaseId, testName, baseUrl, steps, null);
    }

    public record PlannedStep(
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
        public PlannedStep {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
            postconditions = postconditions == null ? List.of() : List.copyOf(postconditions);
            semanticConstraints = semanticConstraints == null ? List.of() : List.copyOf(semanticConstraints);
        }

        public PlannedStep(String id, String action, String target, String value, String assertion) {
            this(id, action, target, value, assertion, null, null);
        }

        public PlannedStep(String id, String action, String target, String value, String assertion, IntentFilter filter) {
            this(id, action, target, value, assertion, filter, null);
        }

        public PlannedStep(
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

        public static PlannedStep from(IntentStep step) {
            if (step == null) {
                return null;
            }
            return new PlannedStep(
                    step.id(),
                    step.action(),
                    step.target(),
                    step.value(),
                    step.assertion(),
                    step.filter(),
                    step.location(),
                    step.scenarioId(),
                    step.targetType(),
                    step.controlType(),
                    step.containerContext(),
                    step.dependsOn(),
                    step.preconditions(),
                    step.expectedState(),
                    step.postconditions(),
                    step.timeoutPolicy(),
                    step.recoveryPolicy(),
                    step.semanticConstraints()
            );
        }
    }
}
