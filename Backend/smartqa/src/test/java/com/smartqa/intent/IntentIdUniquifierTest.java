package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentIdUniquifierTest {

    @Test
    void repairsDuplicateStepIds() {
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                "Dup",
                1.0,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("step1", "click", "A", null, null),
                        new IntentStep("step1", "click", "B", null, null),
                        new IntentStep("step2", "click", "C", null, null)
                ))),
                List.of()
        );
        IntentContract unique = IntentIdUniquifier.uniquify(contract);
        List<IntentStep> steps = unique.scenarios().getFirst().steps();
        assertEquals("step1", steps.get(0).id());
        assertEquals("step1_repaired", steps.get(1).id());
        assertEquals("step2", steps.get(2).id());
        assertUnique(steps.stream().map(IntentStep::id).toList());
    }

    @Test
    void fillsNullAndEmptyIds() {
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                "Blank",
                1.0,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep(null, "click", "A", null, null),
                        new IntentStep("  ", "click", "B", null, null),
                        new IntentStep("ok", "click", "C", null, null)
                ))),
                List.of()
        );
        IntentContract unique = IntentIdUniquifier.uniquify(contract);
        List<IntentStep> steps = unique.scenarios().getFirst().steps();
        assertTrue(steps.get(0).id().matches("s1_s\\d+_[a-f0-9]{8}"));
        assertTrue(steps.get(1).id().matches("s1_s\\d+_[a-f0-9]{8}"));
        assertEquals("ok", steps.get(2).id());
        assertEquals("s1", steps.get(0).scenarioId());
        assertUnique(steps.stream().map(IntentStep::id).toList());
    }

    @Test
    void stampsScenarioIdOnEveryStep() {
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                "Stamp",
                1.0,
                List.of(new IntentScenario("login", "Login", List.of(
                        new IntentStep("a", "click", "A", null, null)
                ))),
                List.of()
        );
        IntentContract unique = IntentIdUniquifier.uniquify(contract);
        assertEquals("login", unique.scenarios().getFirst().steps().getFirst().scenarioId());
        assertEquals("a", unique.scenarios().getFirst().steps().getFirst().id());
    }

    @Test
    void repairsDuplicateScenarioAndStepIdsAcrossScenarios() {
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                "Multi",
                1.0,
                List.of(
                        new IntentScenario("s1", "One", List.of(
                                new IntentStep("shared", "click", "A", null, null)
                        )),
                        new IntentScenario("s1", "Two", List.of(
                                new IntentStep("shared", "click", "B", null, null)
                        ))
                ),
                List.of()
        );
        IntentContract unique = IntentIdUniquifier.uniquify(contract);
        assertEquals("s1", unique.scenarios().get(0).id());
        assertEquals("s1_repaired", unique.scenarios().get(1).id());
        assertNotEquals(
                unique.scenarios().get(0).steps().getFirst().id(),
                unique.scenarios().get(1).steps().getFirst().id());
        Set<String> ids = new HashSet<>();
        unique.scenarios().forEach(s -> s.steps().forEach(step -> ids.add(step.id())));
        assertEquals(2, ids.size());
    }

    @Test
    void validatorAcceptsPreviouslyDuplicateIds() {
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                "Dup",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("tc13_f", "navigate", "application", "https://example.com", null),
                        new IntentStep("tc13_f", "click", "Fashion", null, null)
                ))),
                List.of()
        );
        IntentContract validated = new IntentValidator().validate(contract);
        assertEquals(IntentContract.READY, validated.status());
        assertUnique(validated.scenarios().getFirst().steps().stream().map(IntentStep::id).toList());
    }

    private static void assertUnique(List<String> ids) {
        assertEquals(ids.size(), new HashSet<>(ids).size());
        ids.forEach(id -> assertTrue(id != null && !id.isBlank()));
    }
}
