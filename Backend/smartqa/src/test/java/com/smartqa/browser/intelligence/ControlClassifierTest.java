package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ControlClassifierTest {

    @Test
    void nativeSelectIsClassified() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "select");
        assertEquals(ControlType.NATIVE_SELECT, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void divWithSelectClassIsCustomDropdown() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "div");
        attrs.put("className", "oxd-select-wrapper");
        assertEquals(ControlType.CUSTOM_DROPDOWN, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void divWithAriaExpandedIsCustomDropdown() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "div");
        attrs.put("ariaExpanded", "false");
        assertEquals(ControlType.CUSTOM_DROPDOWN, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void buttonWithAriaHasPopupListboxIsCustomDropdown() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "button");
        attrs.put("ariaHasPopup", "listbox");
        assertEquals(ControlType.CUSTOM_DROPDOWN, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void comboboxRoleIsCombobox() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "div");
        attrs.put("role", "combobox");
        assertEquals(ControlType.COMBOBOX, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void inputIsTextbox() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "input");
        attrs.put("type", "text");
        assertEquals(ControlType.TEXTBOX, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void controlTypeDrivesActionType() {
        assertTrue(ControlType.NATIVE_SELECT.supportsSelect());
        assertTrue(ControlType.CUSTOM_DROPDOWN.supportsSelect());
        assertTrue(ControlType.COMBOBOX.supportsSelect());
        assertFalse(ControlType.TEXTBOX.supportsSelect());
        assertFalse(ControlType.BUTTON.supportsSelect());
    }

    @Test
    void profileIconButtonIsClassified() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "button");
        attrs.put("ariaLabel", "My Account");
        attrs.put("hasSvg", "true");
        assertEquals(ControlType.PROFILE_BUTTON, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void iconOnlyButtonIsClassifiedAsIconButton() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "button");
        attrs.put("hasSvg", "true");
        assertEquals(ControlType.ICON_BUTTON, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void weakAriaLabelIconButtonIsNotForcedToProfile() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "button");
        attrs.put("ariaLabel", "Button Double Tap to perform action");
        attrs.put("hasSvg", "true");
        assertEquals(ControlType.ICON_BUTTON, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void cartIconButtonIsClassified() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "button");
        attrs.put("ariaLabel", "Shopping cart");
        attrs.put("hasSvg", "true");
        assertEquals(ControlType.CART_BUTTON, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void searchIconButtonIsClassified() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "button");
        attrs.put("title", "Search");
        attrs.put("hasSvg", "true");
        assertEquals(ControlType.SEARCH_BUTTON, ControlClassifier.classifyFromAttributes(attrs));
    }

    @Test
    void menuIconButtonIsClassified() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("tag", "button");
        attrs.put("ariaLabel", "Open menu");
        attrs.put("hasSvg", "true");
        assertEquals(ControlType.MENU_BUTTON, ControlClassifier.classifyFromAttributes(attrs));
    }

    private Map<String, String> baseAttrs() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("tag", "");
        attrs.put("role", "");
        attrs.put("type", "");
        attrs.put("ariaHasPopup", "");
        attrs.put("ariaAutocomplete", "");
        attrs.put("ariaLabel", "");
        attrs.put("title", "");
        attrs.put("text", "");
        attrs.put("className", "");
        attrs.put("hasSvg", "false");
        attrs.put("hasListbox", "false");
        attrs.put("hasOptions", "false");
        return attrs;
    }
}
