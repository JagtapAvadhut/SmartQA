package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstructionIntentCompilerTest {

    @Test
    void compilesCheckboxFilterFlowWithoutDropdown() {
        String instructions = """
                open https://example.com/
                close login popup/box
                click on fashion tab
                click on korean store
                click on K-drama romance
                Inside Filter section
                open Brand section
                select acuarte ABCD checkbox
                verify 3 product is showing only
                """;
        InstructionIntentCompiler.Result result = InstructionIntentCompiler.compile(
                instructions, "https://example.com/");
        assertTrue(result.highConfidence());
        assertTrue(result.usable());
        var steps = result.contract().scenarios().getFirst().steps();
        IntentStep checkbox = steps.stream()
                .filter(step -> SupportedActions.CHECKBOX.equals(step.action()))
                .findFirst()
                .orElseThrow();
        assertEquals("ABCD", checkbox.target());
        assertEquals("ABCD", checkbox.value());
        assertTrue(steps.stream().noneMatch(step ->
                SupportedActions.SELECT.equals(step.action())
                        && step.target() != null
                        && step.target().toLowerCase().contains("dropdown")));
        assertTrue(steps.stream().anyMatch(step ->
                SupportedActions.VERIFY.equals(step.action())));
    }

    @Test
    void classificationPhrases() {
        assertEquals(SupportedActions.CHECKBOX,
                ActionSemanticNormalizer.rewrite("select", "Brand AK checkbox", null).action());
        assertEquals(SupportedActions.SELECT,
                ActionSemanticNormalizer.rewrite("choose", "from Brand dropdown", "HP").action());
        assertEquals(SupportedActions.RADIO,
                ActionSemanticNormalizer.rewrite("select", "Samsung radio button", null).action());
        assertEquals(SupportedActions.INPUT,
                ActionSemanticNormalizer.rewrite("enter", "laptop in textbox", null).action());
        assertEquals(SupportedActions.CLICK,
                ActionSemanticNormalizer.rewrite("click", "Login button", null).action());
        assertEquals(SupportedActions.EXPAND,
                ActionSemanticNormalizer.rewrite("select", "select BRAND and open .", null).action());
    }

    @Test
    void informalFilterInstructionsCompileAndValidate() {
        String instructions = """
                click on Fashion tab .
                click on korean store .
                click on Seoul streetwear image .
                inside filter click on CLEAR ALL .
                select BRAND and open .
                inside brand click on Search Brand .
                inside this search brand search AK .and enter
                selct AK check box.
                verify product name is : Women Striped Round Neck Pure Cotton Pink
                """;
        InstructionIntentCompiler.Result result = InstructionIntentCompiler.compile(
                instructions, "https://example.com/");
        assertTrue(result.usable());
        var steps = result.contract().scenarios().getFirst().steps();
        assertTrue(steps.stream().anyMatch(step ->
                SupportedActions.EXPAND.equals(step.action())
                        && step.target() != null
                        && step.target().toLowerCase().contains("brand")));
        assertTrue(steps.stream().noneMatch(step ->
                SupportedActions.SELECT.equals(step.action())
                        && (step.value() == null || step.value().isBlank())));
        IntentContract validated = new IntentValidator().validate(
                IntentSelectWithoutValueNormalizer.normalize(result.contract()));
        assertEquals(IntentContract.READY, validated.status());
    }

    @Test
    void normalizesTyposAndScopeAndCompoundActions() {
        String instructions = """
                open https://example.com
                insite Brand seach AK then clcik the first product
                hover over cart
                submit login form
                wait until results load
                clear filters
                set quantity to 2
                """;
        InstructionIntentCompiler.Result result = InstructionIntentCompiler.compile(
                instructions, "https://example.com");
        assertTrue(result.usable());
        var steps = result.contract().scenarios().getFirst().steps();
        assertTrue(steps.stream().anyMatch(step ->
                step.containerContext() != null && step.containerContext().toLowerCase().contains("brand")));
        assertTrue(steps.stream().anyMatch(step -> SupportedActions.HOVER.equals(step.action())));
        assertTrue(steps.stream().anyMatch(step -> SupportedActions.SUBMIT.equals(step.action())));
        assertTrue(steps.stream().anyMatch(step -> SupportedActions.WAIT_FOR_STATE.equals(step.action())));
        assertTrue(steps.stream().anyMatch(step -> SupportedActions.CLEAR_FILTERS.equals(step.action())));
        assertTrue(steps.stream().anyMatch(step -> SupportedActions.SET_VALUE.equals(step.action())));
        assertTrue(steps.stream().anyMatch(step ->
                step.semanticConstraints() != null && step.semanticConstraints().stream().anyMatch(c -> c.startsWith("risk:"))));
    }

    @Test
    void compilesBrandDropdownSearchCheckboxAndVerifyTextAs() {
        String instructions = """
                Navigate to 'https://example.com/cars'
                click on the 'Buy a Car' button
                click on the Brand & Model dropdown and search 'volvo' in the search box
                click 'checkbox' near VOLVO in the ALL BRANDS
                Verify text as '5 cars matching your search'
                """;
        InstructionIntentCompiler.Result result = InstructionIntentCompiler.compile(
                instructions, "https://example.com/cars");
        assertTrue(result.usable());
        var steps = result.contract().scenarios().getFirst().steps();
        IntentStep expand = steps.stream()
                .filter(step -> SupportedActions.EXPAND.equals(step.action()))
                .findFirst()
                .orElseThrow();
        assertEquals("Brand & Model", expand.target());
        assertTrue(steps.stream().noneMatch(step ->
                SupportedActions.CHECKBOX.equals(step.action())
                        && step.target() != null
                        && step.target().toLowerCase().contains("model")
                        && !step.target().equalsIgnoreCase("VOLVO")));
        assertTrue(steps.stream().noneMatch(step ->
                SupportedActions.SELECT.equals(step.action())
                        && "Model".equalsIgnoreCase(step.value())));
        IntentStep search = steps.stream()
                .filter(step -> SupportedActions.SEARCH.equals(step.action()))
                .findFirst()
                .orElseThrow();
        assertEquals("volvo", search.value().toLowerCase());
        assertTrue(search.containerContext() == null
                || search.containerContext().toLowerCase().contains("brand"));
        IntentStep checkbox = steps.stream()
                .filter(step -> SupportedActions.CHECKBOX.equals(step.action()))
                .findFirst()
                .orElseThrow();
        assertEquals("VOLVO", checkbox.target());
        assertTrue(checkbox.filter() == null
                || "brand".equalsIgnoreCase(checkbox.filter().field()));
        IntentStep verify = steps.stream()
                .filter(step -> SupportedActions.VERIFY.equals(step.action()))
                .findFirst()
                .orElseThrow();
        assertEquals("5 cars matching your search", verify.target());
        assertEquals("5 cars matching your search", verify.assertion());
        IntentStep navigate = steps.stream()
                .filter(step -> SupportedActions.NAVIGATE.equals(step.action()))
                .findFirst()
                .orElseThrow();
        assertEquals("https://example.com/cars", navigate.value());
    }
}
