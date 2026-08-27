package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.ai.AiProvider;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.ControlType;
import com.smartqa.browser.intelligence.DomExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DropdownLabelAssociationTest {

    @Test
    void selectActionFindsCustomDropdownTriggerNearLabel() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <div class="field-group">
                        <label>User Role</label>
                        <div class="custom-select" aria-expanded="false">-- Select --</div>
                      </div>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), intelligence);
            ElementResolver.ResolvedElement resolved = resolver.resolve(page, "select", "User Role");
            assertNotNull(resolved);
            assertTrue(resolved.locator().isVisible());
            assertTrue(resolved.controlType() == ControlType.CUSTOM_DROPDOWN
                    || resolved.controlType() == ControlType.BUTTON
                    || resolved.controlType() == ControlType.COMBOBOX);
            browser.close();
        }
    }

    @Test
    void selectActionChoosesUniqueDropdownWhenMultipleWrappersExist() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <div class="oxd-form-row">
                        <div class="field-group">
                          <label>User Role</label>
                          <div class="select-wrapper" aria-expanded="false">-- Select --</div>
                        </div>
                        <div class="field-group">
                          <label>Status</label>
                          <div class="select-wrapper" aria-expanded="false">-- Select --</div>
                        </div>
                      </div>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), intelligence);
            ElementResolver.ResolvedElement resolved = resolver.resolve(page, "select", "User Role");
            assertNotNull(resolved);
            assertTrue("label".equals(resolved.locatorType())
                            || "role".equals(resolved.locatorType())
                            || resolved.locator().count() == 1,
                    "Persisted locator must be unique or label/role based, was "
                            + resolved.locatorType() + "=" + resolved.resolvedLocator());
            if ("css".equals(resolved.locatorType())) {
                assertEquals(1, page.locator(resolved.resolvedLocator()).count(),
                        "CSS locator must match exactly one element: " + resolved.resolvedLocator());
            }
            assertNotEquals("div.select-wrapper", resolved.resolvedLocator());
            browser.close();
        }
    }
}
