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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LabelDropdownAssociationTest {

    @Test
    void selectActionResolvesDropdownInSameFieldGroupNotEarlierDropdown() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <form>
                        <div class="grid-item">
                          <label>User Role</label>
                          <div id="role-trigger" class="select-wrapper" tabindex="0">-- Select --</div>
                        </div>
                        <div class="grid-item">
                          <label>Status</label>
                          <div id="status-trigger" class="select-wrapper" tabindex="0">-- Select --</div>
                        </div>
                      </form>
                      <table role="table">
                        <thead>
                          <tr>
                            <th role="columnheader">User Role</th>
                            <th role="columnheader">Status</th>
                          </tr>
                        </thead>
                      </table>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), intelligence);

            ElementResolver.ResolvedElement role = resolver.resolve(page, "select", "User Role");
            ElementResolver.ResolvedElement status = resolver.resolve(page, "select", "Status");

            assertTrue(role.controlType() == ControlType.CUSTOM_DROPDOWN
                    || role.controlType() == ControlType.COMBOBOX);
            assertTrue(status.controlType() == ControlType.CUSTOM_DROPDOWN
                    || status.controlType() == ControlType.COMBOBOX);
            assertNotEquals(role.resolvedLocator(), status.resolvedLocator());
            assertTrue(role.locator().evaluate("el => el.id").toString().contains("role-trigger"));
            assertTrue(status.locator().evaluate("el => el.id").toString().contains("status-trigger"));
            browser.close();
        }
    }
}
