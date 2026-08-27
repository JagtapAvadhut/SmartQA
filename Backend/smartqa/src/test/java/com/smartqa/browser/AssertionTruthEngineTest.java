package com.smartqa.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionTruthEngineTest {

    @Test
    void passwordCriteriaIsBusinessMismatchNotLocatorMiss() {
        AssertionTruthEngine.Verdict verdict = AssertionTruthEngine.evaluate(
                "Passwords do not match",
                "The password does not meet the required criteria",
                "https://opensource-demo.orangehrmlive.com/web/index.php/admin/saveSystemUser",
                "OrangeHRM");
        assertEquals(AssertionTruthEngine.Outcome.BUSINESS_STATE_MISMATCH, verdict.outcome());
        assertTrue(verdict.userMessage().contains("EXPECTED:"));
        assertTrue(verdict.userMessage().contains("Passwords do not match"));
        assertTrue(verdict.userMessage().contains("The password does not meet the required criteria"));
    }

    @Test
    void dashboardWhileStillOnLoginIsLoginStateFailure() {
        AssertionTruthEngine.Verdict verdict = AssertionTruthEngine.evaluate(
                "Dashboard",
                "Username Password Login",
                "https://opensource-demo.orangehrmlive.com/web/index.php/auth/login",
                "OrangeHRM");
        assertEquals(AssertionTruthEngine.Outcome.LOGIN_STATE_FAILURE, verdict.outcome());
    }

    @Test
    void visibleExpectedTextPasses() {
        AssertionTruthEngine.Verdict verdict = AssertionTruthEngine.evaluate(
                "Dashboard",
                "Dashboard Time at Work My Actions",
                "https://opensource-demo.orangehrmlive.com/web/index.php/dashboard/index",
                "OrangeHRM");
        assertEquals(AssertionTruthEngine.Outcome.ASSERTION_PASS, verdict.outcome());
    }
}
