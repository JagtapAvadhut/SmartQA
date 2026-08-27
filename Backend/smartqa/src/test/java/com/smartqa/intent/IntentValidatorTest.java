package com.smartqa.intent;

import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntentValidatorTest {

    private final IntentValidator validator = new IntentValidator();

    @Test
    void acceptsReadyNavigateClickVerify() {
        IntentContract contract = new IntentContract(
                "READY",
                "Example",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "navigate", "Open application", "https://example.com", null),
                        new IntentStep("s1_step2", "click", "More information", null, null),
                        new IntentStep("s1_step3", "verify", "heading", null, "visible")
                ))),
                List.of()
        );
        IntentContract validated = validator.validate(contract);
        assertEquals(IntentContract.READY, validated.status());
        assertEquals(3, validated.scenarios().getFirst().steps().size());
    }

    @Test
    void acceptsStructuredFilterStep() {
        IntentContract contract = new IntentContract(
                "READY",
                "Laptops",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "navigate", "Open application", "https://example.com", null, null),
                        new IntentStep(
                                "s1_step2",
                                "filter",
                                "brand",
                                "HP",
                                null,
                                new IntentFilter("brand", "equals", "HP", null, null)
                        )
                ))),
                List.of()
        );
        IntentContract validated = validator.validate(contract);
        assertEquals(IntentContract.READY, validated.status());
        assertEquals("HP", validated.scenarios().getFirst().steps().get(1).filter().value());
    }

    @Test
    void rejectsUnsupportedAction() {
        IntentContract contract = new IntentContract(
                "READY",
                "Example",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "explode", "page", null, null)
                ))),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }

    @Test
    void rejectsClickWithoutTarget() {
        IntentContract contract = new IntentContract(
                "READY",
                "Example",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "click", " ", null, null)
                ))),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }

    @Test
    void clarificationRequiresQuestions() {
        IntentContract contract = new IntentContract(
                "NEEDS_CLARIFICATION",
                "Example",
                0.2,
                List.of(),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }

    @Test
    void rejectsUnsupportedFilterOperator() {
        IntentContract contract = new IntentContract(
                "READY",
                "Laptops",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep(
                                "s1_step1",
                                "filter",
                                "price",
                                "100",
                                null,
                                new IntentFilter("price", "near", "100", null, null)
                        )
                ))),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }

    @Test
    void clickHttpUrlBecomesNavigateAndIsNotTreatedAsLocator() {
        IntentContract contract = new IntentContract(
                "READY",
                "Flipkart",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "click", "https://www.flipkart.com/", null, null)
                ))),
                List.of()
        );
        IntentContract validated = validator.validate(contract);
        IntentStep step = validated.scenarios().getFirst().steps().getFirst();
        assertEquals("navigate", step.action());
        assertEquals("https://www.flipkart.com/", step.target());
    }

    @Test
    void rejectsLocatorShapedIntent() {
        IntentContract contract = new IntentContract(
                "READY",
                "Unsafe",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "click", "xpath=//button[1]", null, null)
                ))),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }

    @Test
    void rejectsPlaywrightLocatorShapedIntent() {
        IntentContract contract = new IntentContract(
                "READY",
                "Unsafe",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "click", "page.locator('button')", null, null)
                ))),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }

    @Test
    void rejectsUnknownDependsOn() {
        IntentStep dependent = new IntentStep("s1_step2", "click", "Login", null, null)
                .withDependsOn(List.of("missing"));
        IntentContract contract = new IntentContract(
                "READY",
                "Dag",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "navigate", "Open application", "https://example.com", null),
                        dependent
                ))),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }

    @Test
    void rejectsCyclicDependsOn() {
        IntentStep a = new IntentStep("a", "click", "One", null, null).withDependsOn(List.of("b"));
        IntentStep b = new IntentStep("b", "click", "Two", null, null).withDependsOn(List.of("a"));
        IntentContract contract = new IntentContract(
                "READY",
                "Cycle",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(a, b))),
                List.of()
        );
        assertThrows(SmartQaException.class, () -> validator.validate(contract));
    }
}
