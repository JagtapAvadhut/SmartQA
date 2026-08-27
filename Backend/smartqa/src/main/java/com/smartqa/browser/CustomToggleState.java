package com.smartqa.browser;

import com.microsoft.playwright.Locator;

/**
 * Reads and sets checked state for native and custom toggles (ARIA, CSS, nested input).
 */
public final class CustomToggleState {

    private CustomToggleState() {
    }

    public static boolean isChecked(Locator locator) {
        if (locator == null) {
            return false;
        }
        try {
            Object checked = locator.evaluate("""
                    el => {
                      if (!el) return false;
                      const input = el.matches('input') ? el : el.querySelector('input[type=checkbox], input[type=radio]');
                      if (input && (input.checked || input.getAttribute('aria-checked') === 'true')) return true;
                      const aria = (el.getAttribute('aria-checked') || '').toLowerCase();
                      if (aria === 'true') return true;
                      const cls = (el.className || '').toString().toLowerCase();
                      return cls.includes('checked') || cls.includes('selected') || cls.includes('active');
                    }
                    """);
            return Boolean.TRUE.equals(checked);
        } catch (RuntimeException ex) {
            try {
                return locator.isChecked();
            } catch (RuntimeException ignored) {
                return false;
            }
        }
    }

    public static void ensure(Locator locator, boolean checked) {
        if (locator == null) {
            return;
        }
        Locator target = VisibleLocatorPicker.firstVisibleOrControl(locator);
        if (target == null) {
            target = locator;
        }
        if (isChecked(target) == checked) {
            return;
        }
        try {
            if (checked) {
                target.check(new Locator.CheckOptions().setTimeout(4_000));
            } else {
                target.uncheck(new Locator.UncheckOptions().setTimeout(4_000));
            }
            if (isChecked(target) == checked) {
                return;
            }
        } catch (RuntimeException ignored) {
        }
        target.click(new Locator.ClickOptions().setNoWaitAfter(true).setTimeout(4_000));
    }
}
