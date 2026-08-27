package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorSelectorBuilderTest {

    @Test
    void prefersTestIdThenRoleThenPlaceholder() {
        ElementCandidate element = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "searchbox",
                "accessibleName", "Search",
                "placeholder", "Search",
                "testId", "search",
                "id", "ember123",
                "visible", true,
                "enabled", true
        ), 0);
        List<RankedLocator> locators = LocatorSelectorBuilder.fromElement(element, "input");
        assertEquals("css", locators.getFirst().locatorType());
        assertTrue(locators.getFirst().resolvedLocator().contains("data-testid='search'"));
        assertTrue(locators.stream().anyMatch(item -> "role".equals(item.locatorType())));
        assertTrue(locators.stream().noneMatch(item -> item.resolvedLocator().contains(":nth-child")));
        assertTrue(locators.stream().noneMatch(item -> "#ember123".equals(item.resolvedLocator())));
    }

    @Test
    void svgInsideClickableParentResolvesActionableAncestor() {
        ElementCandidate svg = ElementCandidate.fromMap(Map.of(
                "tag", "svg",
                "hasIcon", true,
                "clickable", false,
                "inHeaderRegion", true,
                "visible", true,
                "enabled", true,
                "targetPath", "header > button:nth-of-type(2) > svg",
                "actionableSelector", "header > button:nth-of-type(2)",
                "actionableTag", "button",
                "actionableRole", "button"
        ), 0);
        List<RankedLocator> locators = LocatorSelectorBuilder.fromElement(svg, "click");
        assertTrue(locators.stream().anyMatch(item ->
                "actionable-ancestor".equals(item.reason())
                        && "header > button:nth-of-type(2)".equals(item.resolvedLocator())));
        // Decorative leaf path must not outrank the actionable host for clicks.
        RankedLocator actionable = locators.stream()
                .filter(item -> "actionable-ancestor".equals(item.reason()))
                .findFirst()
                .orElseThrow();
        assertTrue(actionable.confidence() >= 0.9);
        assertTrue(locators.stream().noneMatch(item ->
                "dom-path".equals(item.reason())
                        && item.resolvedLocator().endsWith("> svg")));
    }

    @Test
    void weakAriaLabelStillKeepsActionableAncestorLocator() {
        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("tag", "button");
        raw.put("role", "button");
        raw.put("ariaLabel", "Button Double Tap to perform action");
        raw.put("accessibleName", "Button Double Tap to perform action");
        raw.put("hasIcon", true);
        raw.put("clickable", true);
        raw.put("inHeaderRegion", true);
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("actionableSelector", "#header-actions > button:nth-of-type(3)");
        raw.put("actionableTag", "button");
        raw.put("actionableRole", "button");
        ElementCandidate button = ElementCandidate.fromMap(raw, 0);
        List<RankedLocator> locators = LocatorSelectorBuilder.fromElement(button, "click");
        assertTrue(locators.stream().anyMatch(item -> item.reason().startsWith("actionable-ancestor")));
        assertTrue(locators.stream().anyMatch(item ->
                "css".equals(item.locatorType())
                        && item.resolvedLocator().contains("#header-actions")));
    }

    @Test
    void rejectsMalformedEqualsSelectorFromBuilderPath() {
        ElementCandidate element = ElementCandidate.fromMap(Map.of(
                "tag", "a",
                "text", "Login",
                "visible", true,
                "enabled", true,
                "actionableSelector", "=",
                "targetPath", "=="
        ), 0);
        List<RankedLocator> locators = LocatorSelectorBuilder.fromElement(element, "click");
        assertTrue(locators.stream().noneMatch(item -> {
            String value = item.resolvedLocator() == null ? "" : item.resolvedLocator().trim();
            return "=".equals(value) || "==".equals(value);
        }));
    }
}
