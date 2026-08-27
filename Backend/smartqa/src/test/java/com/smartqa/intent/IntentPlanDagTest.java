package com.smartqa.intent;

import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentPlanDagTest {

    @Test
    void prerequisitesMetWhenDependsOnEmpty() {
        assertTrue(IntentPlanDag.prerequisitesMet(List.of(), Set.of()));
        assertTrue(IntentPlanDag.prerequisitesMet(null, Set.of("a")));
    }

    @Test
    void skipsWhenDependsOnNotCompleted() {
        assertFalse(IntentPlanDag.prerequisitesMet(List.of("login"), Set.of()));
        assertFalse(IntentPlanDag.prerequisitesMet(List.of("login", "search"), Set.of("login")));
        assertTrue(IntentPlanDag.prerequisitesMet(List.of("login"), Set.of("login")));
    }

    @Test
    void inferSequentialDependsOnFillsOmittedEdgesOnly() {
        IntentStep first = new IntentStep("a", "navigate", "Open", "https://example.com", null);
        IntentStep second = new IntentStep("b", "click", "Login", null, null);
        List<IntentStep> inferred = IntentPlanDag.inferSequentialDependsOn(List.of(first, second));
        assertTrue(inferred.getFirst().dependsOn().isEmpty());
        assertEquals(List.of("a"), inferred.get(1).dependsOn());
    }

    @Test
    void rejectsUnknownDependsOnAndCycles() {
        IntentStep a = new IntentStep("a", "click", "One", null, null).withDependsOn(List.of("missing"));
        assertThrows(SmartQaException.class, () -> IntentPlanDag.validate(List.of(a)));
        IntentStep b = new IntentStep("b", "click", "Two", null, null).withDependsOn(List.of("c"));
        IntentStep c = new IntentStep("c", "click", "Three", null, null).withDependsOn(List.of("b"));
        assertThrows(SmartQaException.class, () -> IntentPlanDag.validate(List.of(b, c)));
    }
}
