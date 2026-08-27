package com.smartqa.intent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentContractNormalizerTest {

    private final IntentContractNormalizer normalizer = new IntentContractNormalizer(JsonMapper.builder().build());
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void normalizesOllamaStyleAliasesAndStatus() {
        String raw = """
                {
                  "status": "active",
                  "testName": "Urban Company login",
                  "confidence": 90,
                  "scenarios": [
                    {
                      "name": "Login flow",
                      "steps": [
                        { "action": "navigate", "url": "https://www.urbancompany.com/pune" },
                        { "action": "click", "element": "profile icon" },
                        { "action": "click", "element": "Login" },
                        { "action": "verify", "text": "Enter your phone number" }
                      ]
                    }
                  ]
                }
                """;

        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));

        assertEquals(IntentContract.READY, contract.status());
        assertEquals(0.9, contract.confidence());
        assertEquals(4, contract.scenarios().getFirst().steps().size());
        assertEquals("navigate", contract.scenarios().getFirst().steps().get(0).action());
        assertTrue(contract.scenarios().getFirst().steps().get(0).value().contains("urbancompany.com"));
        assertEquals("profile icon", contract.scenarios().getFirst().steps().get(1).target());
        assertEquals("Login", contract.scenarios().getFirst().steps().get(2).target());
        assertEquals("Enter your phone number", contract.scenarios().getFirst().steps().get(3).target());
        assertEquals(LocationHint.AUTO, contract.scenarios().getFirst().steps().get(1).location());
        assertEquals("s1", contract.scenarios().getFirst().steps().get(1).scenarioId());
        assertNotNull(contract.scenarios().getFirst().steps().get(1).id());
    }

    @Test
    void emptyFilterStringBecomesNullWithoutCrashing() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Empty filter regression",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com", "filter": "" },
                        { "id": "s1_step2", "action": "click", "target": "Login", "filter": "null", "location": "" },
                        { "id": "s1_step3", "action": "click", "target": "profile icon", "location": "TOP_RIGHT", "filter": null }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertNull(contract.scenarios().getFirst().steps().get(0).filter());
        assertNull(contract.scenarios().getFirst().steps().get(1).filter());
        assertEquals(LocationHint.AUTO, contract.scenarios().getFirst().steps().get(1).location());
        assertEquals(LocationHint.TOP_RIGHT, contract.scenarios().getFirst().steps().get(2).location());
    }

    @Test
    void jacksonDirectEmptyFilterStringDoesNotCrash() throws Exception {
        String json = """
                {
                  "id": "s1_step1",
                  "action": "click",
                  "target": "Login",
                  "value": null,
                  "assertion": null,
                  "filter": "",
                  "location": "AUTO"
                }
                """;
        IntentStep step = mapper.readValue(json, IntentStep.class);
        assertNotNull(step);
        assertNull(step.filter());
        assertEquals("Login", step.target());
    }

    @Test
    void searchWithoutTargetDefaultsAndPromotesQuery() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Search phones",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "search", "value": "Smartphones" },
                        { "id": "s1_step3", "action": "search", "target": "samsung smartphone" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("search", contract.scenarios().getFirst().steps().get(1).target());
        assertEquals("samsung smartphone", contract.scenarios().getFirst().steps().get(1).value());
        assertEquals("search", contract.scenarios().getFirst().steps().get(2).target());
        assertEquals("samsung smartphone", contract.scenarios().getFirst().steps().get(2).value());
    }

    @Test
    void pressKeyWithoutValueDefaultsToEnter() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Press enter after city",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "input", "target": "city", "value": "mumbai" },
                        { "id": "s1_step3", "action": "press_key" },
                        { "id": "s1_step4", "action": "press", "target": "enter" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("Enter", contract.scenarios().getFirst().steps().get(2).value());
        assertEquals("press_key", contract.scenarios().getFirst().steps().get(3).action());
        assertEquals("Enter", contract.scenarios().getFirst().steps().get(3).value());
    }

    @Test
    void semanticizesSimpleCssLikeTargets() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Login",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "click", "target": ".profile-icon" },
                        { "id": "s1_step3", "action": "click", "target": "#login-button" },
                        { "id": "s1_step4", "action": "verify", "target": "Enter your phone number" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("profile icon", contract.scenarios().getFirst().steps().get(1).target());
        assertEquals("login button", contract.scenarios().getFirst().steps().get(2).target());
    }

    @Test
    void objectShapedValueIsCoercedToString() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Object value",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "search", "target": "search", "value": { "query": "Smartphones" } },
                        { "id": "s1_step3", "action": "input", "target": "city", "value": { "text": "mumbai" } }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("Smartphones", contract.scenarios().getFirst().steps().get(1).value());
        assertEquals("mumbai", contract.scenarios().getFirst().steps().get(2).value());
    }

    @Test
    void findActionMapsToScrollOrClick() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Find aliases",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "find", "target": "Price Details section" },
                        { "id": "s1_step3", "action": "find", "target": "ADD TO CART" },
                        { "id": "s1_step4", "action": "click", "value": "Login" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("scroll", contract.scenarios().getFirst().steps().get(1).action());
        assertEquals("Price Details section", contract.scenarios().getFirst().steps().get(1).target());
        assertEquals(SupportedActions.ADD_TO_CART, contract.scenarios().getFirst().steps().get(2).action());
        assertEquals("ADD TO CART", contract.scenarios().getFirst().steps().get(2).target());
        assertEquals("Login", contract.scenarios().getFirst().steps().get(3).target());
    }

    @Test
    void findProductQueryMapsToSearch() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Find product query",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "find", "target": "bluetooth speaker" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("search", contract.scenarios().getFirst().steps().get(1).action());
        assertEquals("search", contract.scenarios().getFirst().steps().get(1).target());
        assertEquals("bluetooth speaker", contract.scenarios().getFirst().steps().get(1).value());
    }

    @Test
    void switchToNewTabMapsToCanonicalContextAction() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "New tab alias",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "switch_to_new_tab" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("switch_to_new_tab", contract.scenarios().getFirst().steps().get(1).action());
    }

    @Test
    void filterSalvagesFieldAndValueFromTarget() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Filter salvage",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "filter", "target": "Brand HP" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        var filterStep = contract.scenarios().getFirst().steps().get(1);
        assertEquals("filter", filterStep.action());
        assertNotNull(filterStep.filter());
        assertEquals("Brand", filterStep.filter().field());
        assertEquals("HP", filterStep.filter().value());
    }


    @Test
    void filterParsesPriceRangeFromTarget() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Filter range",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "filter", "target": "Price 40000 to 60000" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        var filter = contract.scenarios().getFirst().steps().get(1).filter();
        assertNotNull(filter);
        assertEquals("Price", filter.field());
        assertEquals("between", filter.operator());
        assertEquals(40000d, filter.min());
        assertEquals(60000d, filter.max());
    }

    @Test
    void compoundClickActionAndSelectAliasesNormalize() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Compound aliases",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "click profile icon" },
                        { "id": "s1_step3", "action": "select", "option": "ESS" },
                        { "id": "s1_step4", "action": "click", "what": "Login" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("click", contract.scenarios().getFirst().steps().get(1).action());
        assertEquals("profile icon", contract.scenarios().getFirst().steps().get(1).target());
        assertEquals("select", contract.scenarios().getFirst().steps().get(2).action());
        assertEquals("dropdown", contract.scenarios().getFirst().steps().get(2).target());
        assertEquals("ESS", contract.scenarios().getFirst().steps().get(2).value());
        assertEquals("Login", contract.scenarios().getFirst().steps().get(3).target());
    }

    @Test
    void clickOnUsernameWithValueBecomesInput() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Login fill",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "click", "target": "Username", "value": "Admin" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("input", contract.scenarios().getFirst().steps().getFirst().action());
        assertEquals("Admin", contract.scenarios().getFirst().steps().getFirst().value());
    }

    @Test
    void genericSearchAdoptsLaterSpecificProductPhrase() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Search salvage",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "search", "target": "search", "value": "Smartphones" },
                        { "id": "s1_step2", "action": "click", "target": "samsung smartphone" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals("samsung smartphone", contract.scenarios().getFirst().steps().getFirst().value());
    }

    @Test
    void clickHttpUrlBecomesNavigate() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Open site",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "click", "target": "https://www.flipkart.com/" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        IntentStep step = contract.scenarios().getFirst().steps().getFirst();
        assertEquals("navigate", step.action());
        assertEquals("https://www.flipkart.com/", step.target());
    }

    @Test
    void openUrlPhraseBecomesNavigate() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Open site",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "open https://example.com" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        IntentStep step = contract.scenarios().getFirst().steps().getFirst();
        assertEquals("navigate", step.action());
        assertEquals("https://example.com", step.target());
    }

    @Test
    void selectAcuarteAbcdCheckboxIsNeverDropdown() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Filter checkbox",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "select", "target": "acuarte ABCD checkbox" },
                        { "id": "s1_step3", "action": "select", "target": "dropdown", "value": "acuarte ABCD checkbox" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        IntentStep first = contract.scenarios().getFirst().steps().get(1);
        IntentStep second = contract.scenarios().getFirst().steps().get(2);
        assertEquals("checkbox", first.action());
        assertEquals("ABCD", first.target());
        assertEquals("checkbox", second.action());
        assertEquals("ABCD", second.target());
        assertFalse(first.target().toLowerCase().contains("dropdown"));
        assertFalse(second.target().toLowerCase().contains("dropdown"));
    }

    @Test
    void duplicateStepIdsAreRepairedBeforeValidate() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Duplicate ids",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "step1", "action": "click", "target": "Fashion" },
                        { "id": "step2", "action": "click", "target": "Korean Store" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        var ids = contract.scenarios().getFirst().steps().stream().map(IntentStep::id).toList();
        assertEquals(3, ids.size());
        assertEquals(3, ids.stream().distinct().count());
        assertEquals("step1", ids.get(0));
        assertEquals("step1_repaired", ids.get(1));
        assertEquals("step2", ids.get(2));
    }

    @Test
    void selectBrandAkCheckboxKeepsCheckbox() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Brand checkbox",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "s1_step1", "action": "navigate", "value": "https://example.com" },
                        { "id": "s1_step2", "action": "select", "target": "Brand AK checkbox" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        IntentStep step = contract.scenarios().getFirst().steps().get(1);
        assertEquals("checkbox", step.action());
        assertEquals("AK", step.target());
        assertEquals(ControlPhrase.CHECKBOX, step.controlType());
        assertEquals("FILTER_OPTION", step.targetType());
        assertNotNull(step.filter());
        assertEquals("Brand", step.filter().field());
        assertEquals("AK", step.filter().value());
    }

    @Test
    void persistsDependsOnWithoutLocatorLeak() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Dag",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "id": "nav", "action": "navigate", "value": "https://example.com" },
                        { "id": "login", "action": "click", "target": "Login", "dependsOn": ["nav"],
                          "preconditions": ["page loaded"], "expectedState": "login form visible" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        IntentStep login = contract.scenarios().getFirst().steps().get(1);
        assertEquals(List.of("nav"), login.dependsOn());
        assertEquals(List.of("page loaded"), login.preconditions());
        assertEquals("login form visible", login.expectedState());
    }

    @Test
    void infersSequentialDependsOnWhenOmitted() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Sequential dag",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "action": "navigate", "value": "https://example.com" },
                        { "action": "click", "target": "Login" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        IntentStep first = contract.scenarios().getFirst().steps().get(0);
        IntentStep second = contract.scenarios().getFirst().steps().get(1);
        assertTrue(first.dependsOn().isEmpty());
        assertEquals(List.of(first.id()), second.dependsOn());
    }

    @Test
    void canonicalizesNewTabAndSelectOptionAliases() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Aliases",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "action": "navigate", "value": "https://example.com" },
                        { "action": "click", "target": "product" },
                        { "action": "new_tab", "target": "product" },
                        { "action": "select_option", "target": "Role", "value": "Admin" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        assertEquals(SupportedActions.SWITCH_TO_NEW_TAB, contract.scenarios().getFirst().steps().get(2).action());
        assertEquals(SupportedActions.SELECT, contract.scenarios().getFirst().steps().get(3).action());
        assertEquals("Role", contract.scenarios().getFirst().steps().get(3).target());
        assertEquals("Admin", contract.scenarios().getFirst().steps().get(3).value());
    }

    @Test
    void promotesFilterFieldAndFilterValue() {
        String raw = """
                {
                  "status": "READY",
                  "testName": "Filter fields",
                  "confidence": 1.0,
                  "scenarios": [
                    {
                      "id": "s1",
                      "name": "Main",
                      "steps": [
                        { "action": "navigate", "value": "https://example.com" },
                        { "action": "filter", "filterField": "Brand", "filterValue": "AK" }
                      ]
                    }
                  ]
                }
                """;
        IntentContract contract = new IntentValidator().validate(normalizer.parse(raw));
        IntentStep filter = contract.scenarios().getFirst().steps().get(1);
        assertEquals(SupportedActions.FILTER, filter.action());
        assertEquals("Brand", filter.filterField());
        assertEquals("AK", filter.filterValue());
    }
}
