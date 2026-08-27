package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.browser.intelligence.ControlType;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

/**
 * Inspects the live control and dispatches to native select, custom dropdown, range, or input.
 * Never forces NativeSelectHandler on a non-select control.
 */
public final class SelectControlDispatcher {

    private SelectControlDispatcher() {
    }

    public static void select(Page page, Locator locator, ControlType controlType, String wanted) {
        if (locator == null || wanted == null || wanted.isBlank()) {
            return;
        }
        String tag = safeTag(locator);
        String type = safeAttr(locator, "type");
        ControlType resolved = controlType == null ? ControlType.OTHER : controlType;

        if ("select".equals(tag) || resolved == ControlType.NATIVE_SELECT && "select".equals(tag)) {
            TraceLogger.info("FILTER", "SELECT_CONTROL_NATIVE", "Dispatching native select", TraceMeta.of(
                    "wanted", wanted,
                    "tag", tag
            ));
            NativeSelectHandler.selectOption(page, locator, wanted);
            return;
        }
        if ("range".equals(type) || resolved == ControlType.RANGE_INPUT) {
            TraceLogger.info("FILTER", "SELECT_CONTROL_RANGE", "Dispatching range input", TraceMeta.of("wanted", wanted));
            applyRangeOrFill(locator, wanted);
            return;
        }
        if ("input".equals(tag) || "textarea".equals(tag) || resolved == ControlType.TEXTBOX) {
            if ("select".equals(tag)) {
                NativeSelectHandler.selectOption(page, locator, wanted);
                return;
            }
            TraceLogger.info("FILTER", "SELECT_CONTROL_INPUT", "Dispatching fillable control", TraceMeta.of(
                    "wanted", wanted,
                    "tag", tag
            ));
            locator.fill(wanted);
            return;
        }
        TraceLogger.info("FILTER", "SELECT_CONTROL_CUSTOM", "Dispatching custom dropdown", TraceMeta.of(
                "wanted", wanted,
                "tag", tag,
                "controlType", resolved.name()
        ));
        if (page != null) {
            CustomDropdownHandler.selectOption(page, locator, wanted);
        } else {
            locator.fill(wanted);
        }
    }

    private static void applyRangeOrFill(Locator locator, String wanted) {
        try {
            locator.fill(wanted);
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            locator.evaluate("""
                    (el, value) => {
                      el.value = value;
                      el.dispatchEvent(new Event('input', { bubbles: true }));
                      el.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    """, wanted);
        } catch (RuntimeException ignored) {
        }
    }

    private static String safeTag(Locator locator) {
        try {
            Object tag = locator.evaluate("el => (el.tagName || '').toLowerCase()");
            return tag == null ? "" : tag.toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeAttr(Locator locator, String name) {
        try {
            String value = locator.getAttribute(name);
            return value == null ? "" : value;
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
