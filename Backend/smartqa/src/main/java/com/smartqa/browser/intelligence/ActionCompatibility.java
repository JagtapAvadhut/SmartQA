package com.smartqa.browser.intelligence;

import com.smartqa.intent.SupportedActions;

import java.util.Map;
import java.util.Set;

public final class ActionCompatibility {

    public static final String CAPABILITY_MISMATCH = "CAPABILITY_MISMATCH";

    private ActionCompatibility() {
    }

    private static final Set<ControlType> FILLABLE = Set.of(
            ControlType.TEXTBOX, ControlType.COMBOBOX,
            ControlType.DATE_PICKER, ControlType.RANGE_INPUT);

    private static final Set<ControlType> EXPANDABLE = Set.of(
            ControlType.BUTTON, ControlType.ICON_BUTTON, ControlType.MENU_BUTTON,
            ControlType.TAB, ControlType.MENU, ControlType.CUSTOM_DROPDOWN, ControlType.COMBOBOX,
            ControlType.OTHER);

    private static final Map<String, Set<ControlType>> COMPATIBLE = Map.ofEntries(
            Map.entry(SupportedActions.SELECT, Set.of(
                    ControlType.NATIVE_SELECT, ControlType.CUSTOM_DROPDOWN,
                    ControlType.COMBOBOX, ControlType.LISTBOX)),
            Map.entry(SupportedActions.INPUT, FILLABLE),
            Map.entry(SupportedActions.SEARCH, FILLABLE),
            Map.entry(SupportedActions.CHECKBOX, Set.of(ControlType.CHECKBOX)),
            Map.entry(SupportedActions.RADIO, Set.of(ControlType.RADIO)),
            Map.entry(SupportedActions.CLICK, Set.of(
                    ControlType.BUTTON, ControlType.ICON_BUTTON, ControlType.PROFILE_BUTTON,
                    ControlType.CART_BUTTON, ControlType.SEARCH_BUTTON, ControlType.MENU_BUTTON,
                    ControlType.CLOSE_BUTTON, ControlType.BACK_BUTTON, ControlType.LINK, ControlType.CHECKBOX,
                    ControlType.RADIO, ControlType.TAB, ControlType.MENU,
                    ControlType.OPTION, ControlType.NATIVE_SELECT,
                    ControlType.CUSTOM_DROPDOWN, ControlType.COMBOBOX,
                    ControlType.TEXTBOX, ControlType.OTHER)),
            Map.entry(SupportedActions.HOVER, Set.of(
                    ControlType.BUTTON, ControlType.ICON_BUTTON, ControlType.PROFILE_BUTTON,
                    ControlType.CART_BUTTON, ControlType.SEARCH_BUTTON, ControlType.MENU_BUTTON,
                    ControlType.CLOSE_BUTTON, ControlType.BACK_BUTTON, ControlType.LINK, ControlType.MENU,
                    ControlType.TAB, ControlType.OTHER)),
            Map.entry(SupportedActions.EXPAND, EXPANDABLE),
            Map.entry(SupportedActions.COLLAPSE, EXPANDABLE),
            Map.entry(SupportedActions.ADD_TO_CART, Set.of(
                    ControlType.CART_BUTTON, ControlType.BUTTON, ControlType.ICON_BUTTON, ControlType.LINK)),
            Map.entry(SupportedActions.QUANTITY, Set.of(
                    ControlType.BUTTON, ControlType.ICON_BUTTON, ControlType.TEXTBOX, ControlType.OTHER))
    );

    private static final Map<String, ControlCapability> REQUIRED = Map.ofEntries(
            Map.entry(SupportedActions.INPUT, ControlCapability.TEXT_INPUT),
            Map.entry(SupportedActions.SEARCH, ControlCapability.SEARCH),
            Map.entry(SupportedActions.CHECKBOX, ControlCapability.CHECK),
            Map.entry(SupportedActions.RADIO, ControlCapability.CHECK),
            Map.entry(SupportedActions.SELECT, ControlCapability.SELECT_OPTION),
            Map.entry(SupportedActions.CLICK, ControlCapability.CLICK),
            Map.entry(SupportedActions.HOVER, ControlCapability.HOVER),
            Map.entry(SupportedActions.PRESS_KEY, ControlCapability.PRESS_KEY),
            Map.entry(SupportedActions.SCROLL, ControlCapability.SCROLL),
            Map.entry(SupportedActions.VERIFY, ControlCapability.VERIFY),
            Map.entry(SupportedActions.EXPAND, ControlCapability.CLICK),
            Map.entry(SupportedActions.COLLAPSE, ControlCapability.CLICK),
            Map.entry(SupportedActions.ADD_TO_CART, ControlCapability.CLICK),
            Map.entry(SupportedActions.QUANTITY, ControlCapability.CLICK)
    );

    public static boolean isCompatible(String action, ControlType controlType) {
        if (action == null || controlType == null) {
            return true;
        }
        if (isCapabilityMismatch(action, controlType)) {
            return false;
        }
        Set<ControlType> allowed = COMPATIBLE.get(action.toLowerCase());
        if (allowed == null) {
            return true;
        }
        return allowed.contains(controlType);
    }

    public static boolean isCapabilityMismatch(String action, ControlType controlType) {
        if (action == null || controlType == null) {
            return false;
        }
        ControlCapability required = requiredCapability(action);
        if (required == null) {
            return false;
        }
        return !controlType.supports(required);
    }

    public static ControlCapability requiredCapability(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return REQUIRED.get(action.trim().toLowerCase());
    }

    public static boolean requiresRediscovery(String action, ControlType controlType) {
        if (controlType == null) {
            return false;
        }
        if (isFillOrSearch(action) && isButtonLike(controlType)) {
            return true;
        }
        return !isCompatible(action, controlType) && !controlType.isInteractive();
    }

    private static boolean isFillOrSearch(String action) {
        if (action == null) {
            return false;
        }
        String lower = action.toLowerCase();
        return SupportedActions.INPUT.equals(lower) || SupportedActions.SEARCH.equals(lower);
    }

    private static boolean isButtonLike(ControlType controlType) {
        return controlType == ControlType.BUTTON
                || controlType == ControlType.ICON_BUTTON
                || controlType == ControlType.SEARCH_BUTTON
                || controlType == ControlType.MENU_BUTTON;
    }
}
