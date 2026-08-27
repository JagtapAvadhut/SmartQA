package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicPlaywrightFactoryTest {

    @Test
    void nativeSelectUsesSelectOption() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "select", "Country", "select#country", "css",
                0.95, "USA", null, "http://example.com", false, "USA", null, null, "NATIVE_SELECT");
        String code = DeterministicPlaywrightFactory.render("TestClass",
                new LocatorMemoryDocument(List.of(entry)));
        assertTrue(code.contains(".selectOption("), "Native select should use selectOption");
        assertFalse(code.contains("Custom dropdown"), "Should not have custom dropdown comment");
    }

    @Test
    void customDropdownNeverUsesSelectOption() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "select", "User Role", "div.oxd-select-wrapper", "css",
                0.88, "ESS", null, "http://example.com", false, "ESS", null, null, "CUSTOM_DROPDOWN");
        String code = DeterministicPlaywrightFactory.render("TestClass",
                new LocatorMemoryDocument(List.of(entry)));
        assertFalse(code.contains(".selectOption("), "CUSTOM_DROPDOWN must NOT use selectOption");
        assertTrue(code.contains(".click("), "Should click to open dropdown");
        assertTrue(code.contains("AriaRole.OPTION") || code.contains("getByText"),
                "Should look for option by role or text");
        assertFalse(code.contains("waitForTimeout"), "Custom dropdown code must avoid fixed waits");
        assertTrue(code.contains("setNoWaitAfter(true)"),
                "Dropdown clicks must not wait for hanging navigations");
        assertFalse(code.contains("[class*='dropdown']"),
                "Must not treat any dropdown-class node as the open menu");
        assertTrue(code.contains("getByLabel(\"User Role\")"),
                "Labeled custom dropdowns should replay via accessible name, not class CSS");
        assertFalse(code.contains("div.oxd-select-wrapper"),
                "Must not emit ambiguous site-class CSS for custom dropdowns");
        assertTrue(code.contains("ignoredToo"),
                "Listbox wait fallback must be swallowed so missing role=listbox cannot fail validation");
    }

    @Test
    void comboboxNeverUsesSelectOption() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "select", "Status", "div.combo", "css",
                0.88, "Active", null, "http://example.com", false, "Active", null, null, "COMBOBOX");
        String code = DeterministicPlaywrightFactory.render("TestClass",
                new LocatorMemoryDocument(List.of(entry)));
        assertFalse(code.contains(".selectOption("), "COMBOBOX must NOT use selectOption");
        assertTrue(code.contains(".click("), "Should click to open");
    }

    @Test
    void unknownControlTypeFallsBackToSelectOption() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "select", "Color", "select.color", "css",
                0.95, "Red", null, "http://example.com", false, "Red", null, null, null);
        String code = DeterministicPlaywrightFactory.render("TestClass",
                new LocatorMemoryDocument(List.of(entry)));
        assertTrue(code.contains(".selectOption("), "Unknown type should fall back to selectOption");
    }

    @Test
    void controlTypePreservedInLocatorMemory() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "select", "User Role", "div.oxd-select-wrapper", "css",
                0.88, "ESS", null, "http://example.com", false, "ESS", null, null, "CUSTOM_DROPDOWN");
        assertEquals("CUSTOM_DROPDOWN", entry.controlType());
        assertEquals("User Role", entry.semanticTarget());
        assertEquals("ESS", entry.value());
    }

    @Test
    void inputFillConfirmsAutocompleteOptionWhenPresent() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "input", "Employee Name", "input", "css",
                0.85, "", null, "http://example.com", false, "Radha Gupta", null, null, "TEXTBOX");
        String code = DeterministicPlaywrightFactory.render("TestClass",
                new LocatorMemoryDocument(List.of(entry)));
        assertTrue(code.contains("getByLabel(\"Employee Name\")"),
                "Ambiguous CSS input locators should replay via label");
        assertTrue(code.contains("AriaRole.OPTION"), "Input should attempt autocomplete option confirmation");
        assertFalse(code.contains("locator(\"input\")"), "Must not emit generic tag CSS for labeled inputs");
    }

    @Test
    void recordOutcomeVerifyDoesNotRequireInstructionTextOnPage() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "verify", "matching employee record", "records-present", "outcome",
                0.93, "matching employee record", null, "http://example.com", false, null, null);
        String code = DeterministicPlaywrightFactory.render("TestClass",
                new LocatorMemoryDocument(List.of(entry)));
        assertTrue(code.contains("records? found") || code.contains("records? found"),
                "Generated verify should look for records-found outcome, not instruction prose");
        assertFalse(code.contains("getByText(\"matching employee record\")"),
                "Must not require the business-outcome phrase as visible UI text");
    }

    @Test
    void emittedCodeContainsSharedRuntimeHelpers() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "click", "Save", "#save", "css",
                0.9, "Save", null, "http://example.com", false, null, null);
        String code = DeterministicPlaywrightFactory.render("HelperTest",
                new LocatorMemoryDocument(List.of(entry)));
        assertTrue(code.contains("firstVisible("));
        assertTrue(code.contains("clickAndUseResultingPage("));
        assertTrue(code.contains("ensureToggle("));
        assertTrue(code.contains("captureScreenshot("));
    }

    @Test
    void recordFoundVerifyAcceptsPluralInflection() {
        LocatorMemoryEntry entry = new LocatorMemoryEntry(
                "s1", "verify", "Record Found", "Record Found", "text",
                0.95, "Record Found", null, "http://example.com", false, null, null);
        String code = DeterministicPlaywrightFactory.render("TestClass",
                new LocatorMemoryDocument(List.of(entry)));
        assertTrue(code.contains("Records Found"), "Generated assertion must accept Records Found");
        assertTrue(code.contains("Record Found"));
    }
}
