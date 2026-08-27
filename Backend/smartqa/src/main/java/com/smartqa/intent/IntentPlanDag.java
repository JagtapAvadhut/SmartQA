package com.smartqa.intent;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Planning/state metadata for semantic steps. Not a second execution engine.
 */
public final class IntentPlanDag {

    private IntentPlanDag() {
    }

    /**
     * Runtime skip check for the existing sequential loop. Not a second runner.
     */
    public static boolean prerequisitesMet(List<String> dependsOn, Set<String> completedStepIds) {
        if (dependsOn == null || dependsOn.isEmpty()) {
            return true;
        }
        Set<String> completed = completedStepIds == null ? Set.of() : completedStepIds;
        for (String dep : dependsOn) {
            if (dep != null && !dep.isBlank() && !completed.contains(dep)) {
                return false;
            }
        }
        return true;
    }

    public static void validate(List<IntentStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (IntentStep step : steps) {
            if (step != null && step.id() != null && !step.id().isBlank()) {
                ids.add(step.id());
            }
        }
        for (IntentStep step : steps) {
            if (step == null) {
                continue;
            }
            for (String dep : step.dependsOn()) {
                if (dep == null || dep.isBlank()) {
                    continue;
                }
                if (!ids.contains(dep)) {
                    throw new SmartQaException(
                            ErrorCode.INTENT_INVALID,
                            "Step " + step.id() + " dependsOn unknown step '" + dep + "'");
                }
                if (dep.equals(step.id())) {
                    throw new SmartQaException(
                            ErrorCode.INTENT_INVALID,
                            "Step " + step.id() + " cannot depend on itself");
                }
            }
        }
        detectCycles(steps);
    }

    /**
     * When AI omitted dependsOn, sequential scenario order becomes planning metadata only.
     */
    public static List<IntentStep> inferSequentialDependsOn(List<IntentStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return steps == null ? List.of() : steps;
        }
        List<IntentStep> out = new ArrayList<>(steps.size());
        String previousId = null;
        for (IntentStep step : steps) {
            if (step == null) {
                continue;
            }
            if ((step.dependsOn() == null || step.dependsOn().isEmpty()) && previousId != null) {
                out.add(step.withDependsOn(List.of(previousId)));
            } else {
                out.add(step);
            }
            previousId = step.id();
        }
        return List.copyOf(out);
    }

    private static void detectCycles(List<IntentStep> steps) {
        Map<String, List<String>> edges = new HashMap<>();
        for (IntentStep step : steps) {
            if (step == null || step.id() == null) {
                continue;
            }
            edges.put(step.id(), step.dependsOn() == null ? List.of() : step.dependsOn());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : edges.keySet()) {
            if (hasCycle(id, edges, visiting, visited)) {
                throw new SmartQaException(ErrorCode.INTENT_INVALID, "Step dependency cycle involving " + id);
            }
        }
    }

    private static boolean hasCycle(
            String id,
            Map<String, List<String>> edges,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visited.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        for (String next : edges.getOrDefault(id, List.of())) {
            if (next != null && hasCycle(next, edges, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }
}
