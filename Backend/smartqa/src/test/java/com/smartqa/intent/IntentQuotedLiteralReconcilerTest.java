package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentQuotedLiteralReconcilerTest {

    @Test
    void snapsDashboardTextToQuotedDashboard() {
        IntentContract raw = new IntentContract(
                IntentContract.READY,
                "OHRM",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "navigate", "application", "https://example.com", null),
                        new IntentStep("s1_step2", "verify", "Dashboard text", null, "contains")
                ))),
                List.of()
        );
        IntentContract fixed = IntentQuotedLiteralReconciler.reconcile(
                raw,
                "Verify text as \"Dashboard\".\nVerify text as \"No Records Found\"."
        );
        assertEquals("Dashboard", fixed.scenarios().getFirst().steps().get(1).target());
    }

    @Test
    void preservesExactQuotedVerify() {
        IntentContract raw = new IntentContract(
                IntentContract.READY,
                "UC",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "verify", "Enter your phone number", null, "contains")
                ))),
                List.of()
        );
        IntentContract fixed = IntentQuotedLiteralReconciler.reconcile(
                raw, "Verify text as \"Enter your phone number\".");
        assertEquals("Enter your phone number", fixed.scenarios().getFirst().steps().get(0).target());
    }
}
