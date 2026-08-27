package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionCompatibilityTest {

    @Test
    void selectCompatibleWithNativeSelect() {
        assertTrue(ActionCompatibility.isCompatible("select", ControlType.NATIVE_SELECT));
    }

    @Test
    void selectCompatibleWithCustomDropdown() {
        assertTrue(ActionCompatibility.isCompatible("select", ControlType.CUSTOM_DROPDOWN));
    }

    @Test
    void selectCompatibleWithCombobox() {
        assertTrue(ActionCompatibility.isCompatible("select", ControlType.COMBOBOX));
    }

    @Test
    void selectNotCompatibleWithTextbox() {
        assertFalse(ActionCompatibility.isCompatible("select", ControlType.TEXTBOX));
    }

    @Test
    void inputCompatibleWithTextbox() {
        assertTrue(ActionCompatibility.isCompatible("input", ControlType.TEXTBOX));
    }

    @Test
    void searchCompatibleWithTextboxNotSearchButton() {
        assertTrue(ActionCompatibility.isCompatible("search", ControlType.TEXTBOX));
        assertTrue(ActionCompatibility.isCompatible("search", ControlType.COMBOBOX));
        assertFalse(ActionCompatibility.isCompatible("search", ControlType.SEARCH_BUTTON));
        assertFalse(ActionCompatibility.isCompatible("search", ControlType.BUTTON));
        assertFalse(ActionCompatibility.isCompatible("input", ControlType.SEARCH_BUTTON));
    }

    @Test
    void clickCompatibleWithIconControls() {
        assertTrue(ActionCompatibility.isCompatible("click", ControlType.ICON_BUTTON));
        assertTrue(ActionCompatibility.isCompatible("click", ControlType.PROFILE_BUTTON));
        assertTrue(ActionCompatibility.isCompatible("click", ControlType.CART_BUTTON));
        assertTrue(ActionCompatibility.isCompatible("click", ControlType.SEARCH_BUTTON));
        assertTrue(ActionCompatibility.isCompatible("click", ControlType.MENU_BUTTON));
    }

    @Test
    void labelRequiresRediscoveryForSelect() {
        assertTrue(ActionCompatibility.requiresRediscovery("select", ControlType.LABEL));
    }

    @Test
    void nonInteractiveOtherRequiresRediscoveryForInput() {
        assertTrue(ActionCompatibility.requiresRediscovery("input", ControlType.OTHER));
        assertTrue(ActionCompatibility.requiresRediscovery("input", ControlType.HEADING));
        assertFalse(ActionCompatibility.requiresRediscovery("input", ControlType.TEXTBOX));
    }

    @Test
    void checkboxNotCompatibleWithDropdown() {
        assertFalse(ActionCompatibility.isCompatible("checkbox", ControlType.CUSTOM_DROPDOWN));
        assertFalse(ActionCompatibility.isCompatible("checkbox", ControlType.NATIVE_SELECT));
        assertTrue(ActionCompatibility.isCapabilityMismatch("checkbox", ControlType.CUSTOM_DROPDOWN));
        assertEquals(ActionCompatibility.CAPABILITY_MISMATCH, ActionCompatibility.CAPABILITY_MISMATCH);
        assertTrue(ControlType.CHECKBOX.supports(ControlCapability.CHECK));
        assertFalse(ControlType.CUSTOM_DROPDOWN.supports(ControlCapability.CHECK));
    }

    @Test
    void inputNotCompatibleWithButton() {
        assertFalse(ActionCompatibility.isCompatible("input", ControlType.BUTTON));
        assertTrue(ActionCompatibility.isCapabilityMismatch("input", ControlType.BUTTON));
        assertFalse(ControlType.BUTTON.supports(ControlCapability.TEXT_INPUT));
    }

    @Test
    void requiredCapabilityMapsActions() {
        assertEquals(ControlCapability.CHECK, ActionCompatibility.requiredCapability("checkbox"));
        assertEquals(ControlCapability.SELECT_OPTION, ActionCompatibility.requiredCapability("select"));
        assertEquals(ControlCapability.TEXT_INPUT, ActionCompatibility.requiredCapability("input"));
    }

    @Test
    void searchButtonRequiresRediscoveryForSearch() {
        assertTrue(ActionCompatibility.requiresRediscovery("search", ControlType.SEARCH_BUTTON));
        assertTrue(ActionCompatibility.requiresRediscovery("search", ControlType.BUTTON));
        assertFalse(ActionCompatibility.requiresRediscovery("search", ControlType.TEXTBOX));
    }

    @Test
    void expandAndCartActionsHaveCapabilityMaps() {
        assertTrue(ActionCompatibility.isCompatible("expand", ControlType.BUTTON));
        assertTrue(ActionCompatibility.isCompatible("collapse", ControlType.MENU));
        assertTrue(ActionCompatibility.isCompatible("add_to_cart", ControlType.CART_BUTTON));
        assertTrue(ActionCompatibility.isCompatible("quantity", ControlType.BUTTON));
        assertEquals(ControlCapability.CLICK, ActionCompatibility.requiredCapability("expand"));
        assertEquals(ControlCapability.CLICK, ActionCompatibility.requiredCapability("add_to_cart"));
    }
}
