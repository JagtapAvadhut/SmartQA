package com.smartqa.browser.intelligence;

/**
 * Action capabilities a live control can support. Soft ranking never invents a capability.
 */
public enum ControlCapability {
    TEXT_INPUT,
    CLICK,
    CHECK,
    UNCHECK,
    SELECT_VALUE,
    SELECT_OPTION,
    PRESS_KEY,
    HOVER,
    SCROLL,
    EXPAND,
    COLLAPSE,
    SEARCH,
    VERIFY
}
