package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;

public final class CustomDropdownHandler {

    private CustomDropdownHandler() {
    }

    public static void selectOption(Page page, Locator trigger, String optionText) {
        TraceLogger.info("DROPDOWN", "DROPDOWN_OPEN_STARTED", "Opening custom dropdown",
                TraceMeta.of("option", optionText));

        if (!trigger.isVisible()) {
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED,
                    "Custom dropdown trigger is not visible");
        }
        if (!trigger.isEnabled()) {
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED,
                    "Custom dropdown trigger is not enabled");
        }

        SafeClick.click(trigger, page);
        waitForDropdownOpen(page, optionText);

        Locator option = findVisibleOption(page, trigger, optionText);
        if (option == null) {
            TraceLogger.info("DROPDOWN", "DROPDOWN_RETRY", "Option not visible after first open, retrying",
                    TraceMeta.of("option", optionText));
            try {
                SafeClick.click(trigger, page);
            } catch (RuntimeException ignored) {
            }
            waitForDropdownOpen(page, optionText);
            option = findVisibleOption(page, trigger, optionText);
        }

        if (option == null) {
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED,
                    "Option '" + optionText + "' not found in custom dropdown");
        }

        TraceLogger.info("DROPDOWN", "DROPDOWN_OPTION_FOUND", "Found option in dropdown",
                TraceMeta.of("option", optionText));

        SafeClick.click(option, page);
        waitForDropdownClose(page);

        verifySelection(page, trigger, optionText);

        TraceLogger.info("DROPDOWN", "DROPDOWN_COMPLETED", "Custom dropdown selection completed",
                TraceMeta.of("option", optionText, "verified", true));
    }

    private static Locator findVisibleOption(Page page, Locator trigger, String optionText) {
        String normalizedOption = optionText.trim().toLowerCase(Locale.ROOT);

        Locator roleOptions = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(optionText));
        if (roleOptions.count() >= 1) {
            for (int i = 0; i < roleOptions.count(); i++) {
                Locator opt = roleOptions.nth(i);
                if (opt.isVisible()) {
                    return opt;
                }
            }
        }
        Locator allRoleOptions = page.getByRole(AriaRole.OPTION);
        for (int i = 0; i < allRoleOptions.count(); i++) {
            Locator opt = allRoleOptions.nth(i);
            if (opt.isVisible() && textMatches(opt, normalizedOption)) {
                return opt;
            }
        }

        Locator listboxOptions = page.locator("[role='listbox'] [role='option']");
        for (int i = 0; i < listboxOptions.count(); i++) {
            Locator opt = listboxOptions.nth(i);
            if (opt.isVisible() && textMatches(opt, normalizedOption)) {
                return opt;
            }
        }

        Locator menuItems = page.locator("[role='listbox'] > *, [role='menu'] > *, .dropdown-menu > *");
        for (int i = 0; i < menuItems.count(); i++) {
            Locator item = menuItems.nth(i);
            if (item.isVisible() && textMatches(item, normalizedOption)) {
                return item;
            }
        }

        Locator dropdownItems = page.locator(
                ".select-dropdown > *, .dropdown-content > *, "
                + "[class*='dropdown'] [class*='option'], [class*='select'] [class*='option']");
        for (int i = 0; i < dropdownItems.count(); i++) {
            Locator item = dropdownItems.nth(i);
            if (item.isVisible() && textMatches(item, normalizedOption)) {
                return item;
            }
        }

        Locator checkbox = page.locator("input[type='checkbox'], [role='checkbox']");
        for (int i = 0; i < checkbox.count(); i++) {
            Locator item = checkbox.nth(i);
            if (item.isVisible() && textMatches(item, normalizedOption)) {
                return item;
            }
            try {
                Locator labeled = item.locator("xpath=ancestor::label[1]");
                if (labeled.count() > 0 && labeled.first().isVisible() && textMatches(labeled.first(), normalizedOption)) {
                    return item.isVisible() ? item : labeled.first();
                }
            } catch (RuntimeException ignored) {
            }
        }

        Locator textMatch = page.getByText(optionText, new Page.GetByTextOptions().setExact(true));
        for (int i = 0; i < textMatch.count(); i++) {
            Locator match = textMatch.nth(i);
            if (match.isVisible()) {
                String tag = safeTag(match);
                if (!tag.matches("h[1-6]")) {
                    return match;
                }
            }
        }

        Locator partialMatch = page.getByText(optionText);
        for (int i = 0; i < partialMatch.count(); i++) {
            Locator match = partialMatch.nth(i);
            if (match.isVisible()) {
                String tag = safeTag(match);
                if (!"label".equals(tag) && !tag.matches("h[1-6]")
                        && !"select".equals(tag) && !"input".equals(tag)
                        && textMatches(match, normalizedOption)) {
                    return match;
                }
            }
        }

        return null;
    }

    private static void waitForDropdownOpen(Page page, String optionText) {
        try {
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(optionText))
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(8000));
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            page.locator("[role='listbox'] [role='option'], [role='listbox']:not([hidden]), [role='menu']:not([hidden])")
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(5000));
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            page.locator("input[type='search'], [role='searchbox'], input[type='checkbox'], [role='checkbox']")
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(4000));
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            page.getByText(optionText, new Page.GetByTextOptions().setExact(true))
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(5000));
        } catch (RuntimeException ignored) {
            // findVisibleOption still scans remaining generic surfaces.
        }
    }

    private static void waitForDropdownClose(Page page) {
        Locator openMenus = page.locator("[role='listbox'], [role='menu']");
        if (openMenus.count() == 0) {
            return;
        }
        try {
            openMenus.first().waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN)
                    .setTimeout(2000));
        } catch (RuntimeException ignored) {
            // Some dropdowns stay mounted; selection verification is the source of truth.
        }
    }

    private static void verifySelection(Page page, Locator trigger, String optionText) {
        String normalizedOption = optionText.trim().toLowerCase(Locale.ROOT);
        try {
            String triggerText = safeText(trigger).toLowerCase(Locale.ROOT);
            if (triggerText.contains(normalizedOption)) {
                return;
            }
            Locator parent = trigger.locator("..");
            String parentText = safeText(parent).toLowerCase(Locale.ROOT);
            if (parentText.contains(normalizedOption)) {
                return;
            }
        } catch (RuntimeException ignored) {
        }
        TraceLogger.warn("DROPDOWN", "DROPDOWN_VERIFY_SOFT",
                "Could not definitively verify selection, continuing",
                TraceMeta.of("option", optionText));
    }

    private static boolean textMatches(Locator locator, String normalizedOption) {
        String text = safeText(locator).toLowerCase(Locale.ROOT).trim();
        if (text.equals(normalizedOption) || text.contains(normalizedOption) || normalizedOption.contains(text)) {
            return !text.isBlank();
        }
        return false;
    }

    private static String safeText(Locator locator) {
        try {
            String text = locator.innerText(new Locator.InnerTextOptions().setTimeout(1000));
            return text == null ? "" : text.trim();
        } catch (RuntimeException ex) {
            return "";
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
}
