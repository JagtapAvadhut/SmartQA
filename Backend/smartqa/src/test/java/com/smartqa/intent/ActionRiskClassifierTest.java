package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionRiskClassifierTest {

    @Test
    void classifiesPaymentAndDeleteAsHighRisk() {
        assertEquals(ActionRiskClassifier.Level.HIGH, ActionRiskClassifier.classify("click", "Pay now", null));
        assertEquals(ActionRiskClassifier.Level.HIGH, ActionRiskClassifier.classify("click", "Delete account", null));
        assertTrue(ActionRiskClassifier.requiresUniqueWinner(ActionRiskClassifier.Level.HIGH));
        assertEquals(ActionRiskClassifier.Level.LOW, ActionRiskClassifier.classify(SupportedActions.NAVIGATE, "home", null));
    }
}
