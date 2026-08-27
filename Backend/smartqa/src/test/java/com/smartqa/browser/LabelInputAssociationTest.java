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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LabelInputAssociationTest {

    @Test
    void inputActionPrefersLabeledFieldOverTableHeaderWithSameName() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <form>
                        <div class="input-group">
                          <label>Username</label>
                          <div><input id="filter-username"></div>
                        </div>
                      </form>
                      <table role="table">
                        <thead>
                          <tr>
                            <th role="columnheader">Username</th>
                            <th role="columnheader">Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr role="row"><td>Admin</td><td>Enabled</td></tr>
                        </tbody>
                      </table>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), intelligence);
            ElementResolver.ResolvedElement resolved = resolver.resolve(page, "input", "Username field");
            resolved.locator().fill("John");
            assertEquals("John", page.locator("#filter-username").inputValue());
            assertTrue(resolved.controlType() == ControlType.TEXTBOX || resolved.controlType() == ControlType.COMBOBOX);
            browser.close();
        }
    }
}
