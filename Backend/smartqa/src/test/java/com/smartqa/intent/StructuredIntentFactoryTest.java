package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StructuredIntentFactoryTest {

    @Test
    void verifyPromotesAssertionToTargetWhenMissing() {
        IntentContract contract = StructuredIntentFactory.fromSteps(
                "Example",
                "https://example.com",
                List.of(
                        new StructuredIntentFactory.StructuredStepInput(
                                "1", "navigate", null, "https://example.com", null, "AUTO", null),
                        new StructuredIntentFactory.StructuredStepInput(
                                "2", "verify", null, null, "Example Domain", "CONTENT", null)
                ));
        IntentStep step = contract.scenarios().getFirst().steps().get(1);
        assertEquals("verify", step.action());
        assertEquals("Example Domain", step.target());
        assertEquals("Example Domain", step.assertion());
        assertNotNull(step);
    }

    @Test
    void clickHttpUrlBecomesNavigateWithoutDuplicateApplicationStep() {
        IntentContract contract = StructuredIntentFactory.fromSteps(
                "Flipkart",
                "https://www.flipkart.com/",
                List.of(
                        new StructuredIntentFactory.StructuredStepInput(
                                "1", "click", "https://www.flipkart.com/", null, null, "AUTO", null)
                ));
        List<IntentStep> steps = contract.scenarios().getFirst().steps();
        assertEquals(1, steps.size());
        assertEquals("navigate", steps.getFirst().action());
        assertEquals("https://www.flipkart.com/", steps.getFirst().target());
    }
}
