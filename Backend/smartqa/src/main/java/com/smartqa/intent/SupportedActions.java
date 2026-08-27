package com.smartqa.intent;

import java.util.Locale;
import java.util.Set;

public final class SupportedActions {
    public static final String NAVIGATE = "navigate";
    public static final String CLICK = "click";
    public static final String INPUT = "input";
    public static final String SELECT = "select";
    public static final String SELECT_OPTION = "select_option";
    public static final String CHECKBOX = "checkbox";
    public static final String RADIO = "radio";
    public static final String PRESS_KEY = "press_key";
    public static final String HOVER = "hover";
    public static final String WAIT = "wait";
    public static final String VERIFY = "verify";
    public static final String SEARCH = "search";
    public static final String FILTER = "filter";
    public static final String SCROLL = "scroll";
    public static final String SWITCH_TO_NEW_TAB = "switch_to_new_tab";
    public static final String NEW_TAB = "new_tab";
    public static final String EXPAND = "expand";
    public static final String COLLAPSE = "collapse";
    public static final String ADD_TO_CART = "add_to_cart";
    public static final String QUANTITY = "quantity";
    public static final String SUBMIT = "submit";
    public static final String VISUAL_TARGET = "visual_target";
    public static final String WAIT_FOR_STATE = "wait_for_state";
    public static final String CLEAR_FILTERS = "clear_filters";
    public static final String SET_VALUE = "set_value";

    public static final Set<String> ALL = Set.of(
            NAVIGATE, CLICK, INPUT, SELECT, CHECKBOX, RADIO, PRESS_KEY, HOVER, WAIT, VERIFY, SEARCH, FILTER, SCROLL,
            SWITCH_TO_NEW_TAB, EXPAND, COLLAPSE, ADD_TO_CART, QUANTITY, SUBMIT, VISUAL_TARGET, WAIT_FOR_STATE,
            CLEAR_FILTERS, SET_VALUE
    );

    private SupportedActions() {
    }

    /**
     * Maps catalog aliases onto canonical runtime actions. Does not invent a second vocabulary.
     */
    public static String canonicalize(String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        String a = action.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (a) {
            case SELECT_OPTION, "selectoption" -> SELECT;
            case NEW_TAB, "open_new_tab", "switch_tab", "switch_to_tab" -> SWITCH_TO_NEW_TAB;
            case "addtocart", "add_to_bag", "add_to_basket" -> ADD_TO_CART;
            case "qty", "increase_quantity", "increment_quantity", "increase_qty" -> QUANTITY;
            case "open_section", "expand_section" -> EXPAND;
            case "collapse_section" -> COLLAPSE;
            case "submit", "submit_form", "submit_form_button" -> SUBMIT;
            case "visual_target", "visual", "banner" -> VISUAL_TARGET;
            case "wait_for_state", "wait_until", "wait_for" -> WAIT_FOR_STATE;
            case "clear_filters", "clear_filter", "clear_all" -> CLEAR_FILTERS;
            case "set_value", "set" -> SET_VALUE;
            default -> a;
        };
    }
}
