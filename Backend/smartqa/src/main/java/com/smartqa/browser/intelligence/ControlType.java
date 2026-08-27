package com.smartqa.browser.intelligence;

import java.util.Set;

public enum ControlType {
    NATIVE_SELECT,
    CUSTOM_DROPDOWN,
    COMBOBOX,
    TEXTBOX,
    BUTTON,
    ICON_BUTTON,
    PROFILE_BUTTON,
    CART_BUTTON,
    SEARCH_BUTTON,
    MENU_BUTTON,
    CLOSE_BUTTON,
    BACK_BUTTON,
    CHECKBOX,
    RADIO,
    LINK,
    TAB,
    MENU,
    LISTBOX,
    OPTION,
    DATE_PICKER,
    RANGE_INPUT,
    LABEL,
    HEADING,
    TEXT,
    OTHER;

    private static final Set<ControlType> SELECTABLE = Set.of(NATIVE_SELECT, CUSTOM_DROPDOWN, COMBOBOX, LISTBOX);
    private static final Set<ControlType> CLICKABLE = Set.of(BUTTON, ICON_BUTTON, PROFILE_BUTTON, CART_BUTTON,
            SEARCH_BUTTON, MENU_BUTTON, CLOSE_BUTTON, BACK_BUTTON, LINK, CHECKBOX, RADIO, TAB, MENU, OPTION,
            NATIVE_SELECT, CUSTOM_DROPDOWN, COMBOBOX);
    private static final Set<ControlType> INPUTABLE = Set.of(TEXTBOX, COMBOBOX, DATE_PICKER, RANGE_INPUT);
    private static final Set<ControlType> NON_INTERACTIVE = Set.of(LABEL, HEADING, TEXT, OTHER);

    public boolean supportsSelect() {
        return SELECTABLE.contains(this);
    }

    public boolean supportsClick() {
        return CLICKABLE.contains(this);
    }

    public boolean supportsInput() {
        return INPUTABLE.contains(this);
    }

    public boolean isInteractive() {
        return !NON_INTERACTIVE.contains(this);
    }

    public ElementRole coarseRole() {
        return switch (this) {
            case LINK -> ElementRole.NAVIGATION;
            case TEXTBOX, DATE_PICKER, RANGE_INPUT, NATIVE_SELECT, CUSTOM_DROPDOWN, COMBOBOX, LISTBOX ->
                    ElementRole.INPUT;
            case CHECKBOX, RADIO -> ElementRole.TOGGLE;
            case LABEL, HEADING, TEXT, OTHER -> ElementRole.DISPLAY;
            default -> ElementRole.ACTION;
        };
    }

    public boolean supports(ControlCapability capability) {
        return capability != null && capabilities().contains(capability);
    }

    public Set<ControlCapability> capabilities() {
        return switch (this) {
            case TEXTBOX -> Set.of(
                    ControlCapability.TEXT_INPUT, ControlCapability.PRESS_KEY,
                    ControlCapability.SEARCH, ControlCapability.CLICK, ControlCapability.VERIFY);
            case DATE_PICKER -> Set.of(
                    ControlCapability.TEXT_INPUT, ControlCapability.CLICK, ControlCapability.VERIFY);
            case RANGE_INPUT -> Set.of(
                    ControlCapability.TEXT_INPUT, ControlCapability.SELECT_VALUE, ControlCapability.VERIFY);
            case CHECKBOX -> Set.of(
                    ControlCapability.CLICK, ControlCapability.CHECK, ControlCapability.UNCHECK,
                    ControlCapability.VERIFY);
            case RADIO -> Set.of(
                    ControlCapability.CLICK, ControlCapability.CHECK, ControlCapability.VERIFY);
            case NATIVE_SELECT, CUSTOM_DROPDOWN, LISTBOX -> Set.of(
                    ControlCapability.SELECT_VALUE, ControlCapability.SELECT_OPTION, ControlCapability.CLICK,
                    ControlCapability.EXPAND, ControlCapability.COLLAPSE, ControlCapability.VERIFY);
            case COMBOBOX -> Set.of(
                    ControlCapability.SELECT_VALUE, ControlCapability.SELECT_OPTION, ControlCapability.CLICK,
                    ControlCapability.EXPAND, ControlCapability.COLLAPSE, ControlCapability.TEXT_INPUT,
                    ControlCapability.SEARCH, ControlCapability.PRESS_KEY, ControlCapability.VERIFY);
            case OPTION -> Set.of(
                    ControlCapability.CLICK, ControlCapability.SELECT_OPTION, ControlCapability.VERIFY);
            case BUTTON, ICON_BUTTON, PROFILE_BUTTON, CART_BUTTON, SEARCH_BUTTON, MENU_BUTTON,
                    CLOSE_BUTTON, BACK_BUTTON, LINK -> Set.of(
                    ControlCapability.CLICK, ControlCapability.HOVER, ControlCapability.VERIFY);
            case TAB, MENU -> Set.of(
                    ControlCapability.CLICK, ControlCapability.HOVER, ControlCapability.EXPAND,
                    ControlCapability.COLLAPSE, ControlCapability.VERIFY);
            case LABEL, HEADING, TEXT, OTHER -> Set.of(ControlCapability.VERIFY);
        };
    }
}
