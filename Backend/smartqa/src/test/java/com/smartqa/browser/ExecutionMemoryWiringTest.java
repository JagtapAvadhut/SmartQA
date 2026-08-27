package com.smartqa.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionMemoryWiringTest {

    @Test
    void doesNotRememberActionUntilSeparateAssertionPasses() {
        ExecutionPlan.PlannedStep clickWithAssertion = new ExecutionPlan.PlannedStep(
                "s1", "click", "Login", null, "Dashboard is visible");
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "click", "Login", "role=button", "role",
                0.9, "Login", null, "https://example.com", false, null, null);
        assertFalse(PlaywrightBrowserExecutionProvider.shouldRememberSuccess(clickWithAssertion, entry));
    }

    @Test
    void remembersVerifiedActionWithoutPendingAssertion() {
        ExecutionPlan.PlannedStep click = new ExecutionPlan.PlannedStep(
                "s1", "click", "Login", null, null);
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "click", "Login", "role=button", "role",
                0.9, "Login", null, "https://example.com", false, null, null);
        assertTrue(PlaywrightBrowserExecutionProvider.shouldRememberSuccess(click, entry));
    }

    @Test
    void remembersSuccessfulVerify() {
        ExecutionPlan.PlannedStep verify = new ExecutionPlan.PlannedStep(
                "s2", "verify", "heading", null, "Dashboard");
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s2", "verify", "heading", "text=Dashboard", "text",
                0.95, "Dashboard", null, "https://example.com", false, "Dashboard", null);
        assertTrue(PlaywrightBrowserExecutionProvider.shouldRememberSuccess(verify, entry));
    }
}
