package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;
import com.smartqa.browser.PlaywrightBrowserLauncher;
import com.smartqa.browser.VerifyExpectation;
import com.smartqa.intent.SupportedActions;

import java.util.List;
import java.util.Locale;

public final class DeterministicPlaywrightFactory {

    private DeterministicPlaywrightFactory() {
    }

    public static String render(String className, LocatorMemoryDocument memory) {
        StringBuilder body = new StringBuilder();
        if (memory != null && memory.entries() != null) {
            for (LocatorMemoryEntry entry : memory.entries()) {
                body.append(renderStep(entry));
            }
        }
        return """
                import com.microsoft.playwright.Browser;
                import com.microsoft.playwright.BrowserContext;
                import com.microsoft.playwright.BrowserType;
                import com.microsoft.playwright.Locator;
                import com.microsoft.playwright.Page;
                import com.microsoft.playwright.Playwright;
                import com.microsoft.playwright.options.AriaRole;
                import com.microsoft.playwright.options.LoadState;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                public class %s {

                    @Test
                    void shouldRunRecordedFlow() {
                        try (Playwright playwright = Playwright.create()) {
                %s
                            try {
                %s
                            } finally {
                                browser.close();
                            }
                        }
                    }
                %s
                }
                """.formatted(
                className,
                PlaywrightBrowserLauncher.generatedLaunchSnippet("            "),
                body,
                GeneratedRuntimeHelpers.methods());
    }

    private static String renderStep(LocatorMemoryEntry entry) {
        String action = SupportedActions.canonicalize(entry.action() == null ? "" : entry.action().toLowerCase(Locale.ROOT));
        String indent = "                ";
        return switch (action) {
            case SupportedActions.NAVIGATE -> indent + "page.navigate(" + quote(entry.pageUrl()) + ");\n"
                    + indent + "page.waitForLoadState();\n"
                    + indent + "Assertions.assertFalse(page.url() == null || page.url().isBlank());\n";
            case SupportedActions.PRESS_KEY -> indent + "page.keyboard().press(" + quote(firstNonBlank(entry.value(), entry.semanticTarget())) + ");\n";
            case SupportedActions.WAIT -> indent + "page.waitForLoadState();\n";
            case SupportedActions.CLICK, SupportedActions.FILTER,
                 SupportedActions.EXPAND, SupportedActions.COLLAPSE,
                 SupportedActions.ADD_TO_CART, SupportedActions.QUANTITY ->
                    clickNoWait(locatorExpr(entry), indent, true);
            case SupportedActions.VERIFY -> {
                String outcomeCode = renderRecordOutcomeAssertion(entry, indent);
                if (outcomeCode != null) {
                    yield outcomeCode;
                }
                String inflection = renderInflectedTextAssertion(entry, indent);
                if (inflection != null) {
                    yield inflection;
                }
                if ("title".equals(entry.locatorType())) {
                    yield indent + "Assertions.assertTrue(page.title().toLowerCase().contains("
                            + quote(nullToEmpty(entry.elementText()).toLowerCase(Locale.ROOT)) + "));\n";
                }
                String locator = locatorExpr(entry);
                if ("text".equals(entry.locatorType()) || "role".equals(entry.locatorType())) {
                    yield indent + locator + ".first().waitFor();\n"
                            + indent + "Assertions.assertTrue(" + locator + ".first().isVisible());\n";
                }
                yield indent + locator + ".waitFor();\n"
                        + indent + "Assertions.assertTrue(" + locator + ".isVisible());\n";
            }
            case SupportedActions.INPUT, SupportedActions.SEARCH -> indent + locatorExpr(entry) + ".fill(" + quote(entry.value()) + ");\n"
                    + renderAutocompleteConfirm(entry, indent)
                    + ("search".equals(action) ? indent + locatorExpr(entry) + ".press(\"Enter\");\n" : "");
            case SupportedActions.SELECT -> {
                String ct = entry.controlType();
                if ("NATIVE_SELECT".equals(ct)) {
                    yield indent + locatorExpr(entry) + ".selectOption(" + quote(entry.value()) + ");\n";
                } else if ("CUSTOM_DROPDOWN".equals(ct) || "COMBOBOX".equals(ct) || "LISTBOX".equals(ct)) {
                    yield renderCustomDropdownSelect(entry, indent);
                } else {
                    yield indent + locatorExpr(entry) + ".selectOption(" + quote(entry.value()) + ");\n";
                }
            }
            case SupportedActions.CHECKBOX, SupportedActions.RADIO -> indent + "ensureToggle("
                    + locatorExpr(entry) + ", " + ("false".equalsIgnoreCase(entry.value()) ? "false" : "true") + ");\n";
            case SupportedActions.HOVER -> indent + locatorExpr(entry) + ".hover();\n";
            case SupportedActions.SWITCH_TO_NEW_TAB -> indent
                    + "page = com.smartqa.browser.NewPageTracker.switchToNewTab(page, "
                    + "new com.smartqa.browser.NewPageTracker.Capture(java.util.Set.of(page), 1), "
                    + "new java.util.concurrent.atomic.AtomicReference<>(), 5000);\n";
            default -> "";
        };
    }

    private static String renderRecordOutcomeAssertion(LocatorMemoryEntry entry, String indent) {
        String type = entry.locatorType() == null ? "" : entry.locatorType();
        String loc = entry.resolvedLocator() == null ? "" : entry.resolvedLocator();
        VerifyExpectation.RecordOutcome outcome = VerifyExpectation.recordOutcome(
                firstNonBlank(entry.semanticTarget(), entry.elementText(), loc));
        boolean absent = "records-absent".equals(loc) || outcome == VerifyExpectation.RecordOutcome.ABSENT;
        boolean present = "outcome".equals(type)
                || "records-present".equals(loc)
                || outcome == VerifyExpectation.RecordOutcome.PRESENT;
        if (!absent && !present) {
            return null;
        }
        if (absent) {
            return indent + "Assertions.assertTrue(page.getByText(java.util.regex.Pattern.compile("
                    + quote("(?i)no records found|no matching records|no results found")
                    + ")).first().isVisible());\n";
        }
        return indent + "Assertions.assertTrue(\n"
                + indent + "    page.getByText(java.util.regex.Pattern.compile("
                + quote("(?i)\\d+\\s*records? found")
                + ")).count() > 0\n"
                + indent + "        || page.locator("
                + quote("table tbody tr, [role='rowgroup'] [role='row']")
                + ").count() > 0);\n";
    }

    private static String renderInflectedTextAssertion(LocatorMemoryEntry entry, String indent) {
        String expected = firstNonBlank(entry.elementText(), entry.semanticTarget(), entry.resolvedLocator());
        List<String> variants = VerifyExpectation.textVariants(expected);
        if (variants.size() < 2) {
            return null;
        }
        StringBuilder or = new StringBuilder();
        for (int i = 0; i < variants.size(); i++) {
            if (i > 0) {
                or.append("\n").append(indent).append("        || ");
            }
            or.append("page.getByText(").append(quote(variants.get(i))).append(").count() > 0");
        }
        return indent + "Assertions.assertTrue(\n"
                + indent + "        " + or + ");\n";
    }

    private static String locatorExpr(LocatorMemoryEntry entry) {
        if (shouldPreferAccessibleName(entry)) {
            return "page.getByLabel(" + quote(stripControlSuffix(entry.semanticTarget())) + ")";
        }
        String type = entry.locatorType() == null ? "css" : entry.locatorType();
        String locator = entry.resolvedLocator();
        return switch (type) {
            case "text" -> "page.getByText(" + quote(locator) + ")";
            case "label" -> "page.getByLabel(" + quote(locator) + ")";
            case "placeholder" -> "page.getByPlaceholder(" + quote(locator) + ")";
            case "role" -> roleExpr(locator);
            default -> "page.locator(" + quote(locator) + ")";
        };
    }

    private static String roleExpr(String spec) {
        if (spec == null || !spec.contains("|")) {
            return "page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(" + quote(spec) + "))";
        }
        String[] parts = spec.split("\\|", 2);
        String role = switch (parts[0]) {
            case "button" -> "AriaRole.BUTTON";
            case "textbox" -> "AriaRole.TEXTBOX";
            case "searchbox" -> "AriaRole.SEARCHBOX";
            case "combobox" -> "AriaRole.COMBOBOX";
            case "heading" -> "AriaRole.HEADING";
            case "checkbox" -> "AriaRole.CHECKBOX";
            case "radio" -> "AriaRole.RADIO";
            default -> "AriaRole.LINK";
        };
        return "page.getByRole(" + role + ", new Page.GetByRoleOptions().setName(" + quote(parts[1]) + "))";
    }

    private static boolean shouldPreferAccessibleName(LocatorMemoryEntry entry) {
        String target = entry.semanticTarget();
        if (target == null || target.isBlank()) {
            return false;
        }
        String type = entry.locatorType() == null ? "" : entry.locatorType();
        String loc = entry.resolvedLocator() == null ? "" : entry.resolvedLocator().trim();
        if ("label".equals(type) || "role".equals(type)) {
            return false;
        }
        boolean classOnlyCss = "css".equals(type)
                && !loc.startsWith("#")
                && !loc.contains("data-testid")
                && !loc.contains(">")
                && !loc.contains("nth-");
        String ct = entry.controlType();
        if ("CUSTOM_DROPDOWN".equals(ct) || "COMBOBOX".equals(ct) || "LISTBOX".equals(ct)) {
            return classOnlyCss;
        }
        if (!"input".equalsIgnoreCase(entry.action())) {
            return false;
        }
        return classOnlyCss || "input".equals(loc);
    }

    private static String stripControlSuffix(String target) {
        if (target == null) {
            return "";
        }
        return target.replaceAll("(?i)\\s+(dropdown|field|input|button|selector|select|textbox|checkbox|radio|picker)$", "").trim();
    }

    private static String renderAutocompleteConfirm(LocatorMemoryEntry entry, String indent) {
        String value = entry.value();
        if (value == null || value.isBlank() || !looksLikeTypeahead(entry)) {
            return "";
        }
        return indent + "try {\n"
                + indent + "    Locator option = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("
                + quote(value) + "));\n"
                + indent + "    option.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));\n"
                + indent + "    option.first().click(new Locator.ClickOptions().setNoWaitAfter(true));\n"
                + indent + "} catch (RuntimeException ignored) {\n"
                + indent + "}\n";
    }

    private static boolean looksLikeTypeahead(LocatorMemoryEntry entry) {
        String haystack = (nullToEmpty(entry.semanticTarget()) + " " + nullToEmpty(entry.resolvedLocator())
                + " " + nullToEmpty(entry.controlType())).toLowerCase(Locale.ROOT);
        if (haystack.contains("password") || haystack.contains("username")
                || haystack.contains("search") || haystack.contains("email")) {
            return false;
        }
        return haystack.contains("name") || haystack.contains("hint") || haystack.contains("employee")
                || haystack.contains("combobox") || haystack.contains("autocomplete");
    }

    private static String renderCustomDropdownSelect(LocatorMemoryEntry entry, String indent) {
        String locator = locatorExpr(entry);
        String optionText = entry.value();
        return indent + "// Custom dropdown: click to open, then select option\n"
                + clickNoWait(locator, indent, false)
                + indent + "try {\n"
                + indent + "    page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("
                + quote(optionText) + ")).first().waitFor(new Locator.WaitForOptions().setTimeout(8000));\n"
                + indent + "} catch (RuntimeException ignored) {\n"
                + indent + "    try {\n"
                + indent + "        page.locator(\"[role='listbox'] [role='option'], [role='listbox']:not([hidden])\")"
                + ".first().waitFor(new Locator.WaitForOptions().setTimeout(5000));\n"
                + indent + "    } catch (RuntimeException ignoredToo) {\n"
                + indent + "    }\n"
                + indent + "}\n"
                + indent + "{\n"
                + indent + "    Locator option = page.getByRole(AriaRole.OPTION, "
                + "new Page.GetByRoleOptions().setName(" + quote(optionText) + "));\n"
                + indent + "    if (option.count() == 0) {\n"
                + indent + "        option = page.locator(\"[role='listbox'] [role='option']\").filter("
                + "new Locator.FilterOptions().setHasText(" + quote(optionText) + "));\n"
                + indent + "    }\n"
                + indent + "    if (option.count() == 0) {\n"
                + indent + "        option = page.getByText(" + quote(optionText) + ", "
                + "new Page.GetByTextOptions().setExact(true));\n"
                + indent + "    }\n"
                + indent + "    option.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));\n"
                + indent + "    option.first().click(new Locator.ClickOptions().setNoWaitAfter(true));\n"
                + indent + "}\n";
    }

    private static String clickNoWait(String locatorExpr, String indent, boolean settle) {
        if (settle) {
            return indent + "clickAndUseResultingPage(page, firstVisible(" + locatorExpr + "));\n"
                    + indent + "captureScreenshot(page, \"after-click\");\n";
        }
        return indent + "firstVisible(" + locatorExpr + ").click(new Locator.ClickOptions().setNoWaitAfter(true));\n";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String quote(String value) {
        String safe = nullToEmpty(value).replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + safe + "\"";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
