package com.smartqa.browser;

import com.microsoft.playwright.Locator;

/**
 * Picks the first visible match from a locator, with a control-attached fallback
 * for visually hidden native inputs (custom checkboxes/radios).
 */
public final class VisibleLocatorPicker {

    private static final int MAX_SCAN = 8;

    private VisibleLocatorPicker() {
    }

    public static Locator firstVisible(Locator locator) {
        if (locator == null) {
            return null;
        }
        try {
            int count = Math.min(locator.count(), MAX_SCAN);
            for (int i = 0; i < count; i++) {
                Locator item = locator.nth(i);
                if (item.isVisible()) {
                    return item;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Visible node first; if none, the first attached node (hidden native control).
     */
    public static Locator firstVisibleOrControl(Locator locator) {
        Locator visible = firstVisible(locator);
        if (visible != null) {
            return visible;
        }
        if (locator == null) {
            return null;
        }
        try {
            int count = Math.min(locator.count(), MAX_SCAN);
            for (int i = 0; i < count; i++) {
                Locator item = locator.nth(i);
                if (item.count() > 0) {
                    return item;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }
}
