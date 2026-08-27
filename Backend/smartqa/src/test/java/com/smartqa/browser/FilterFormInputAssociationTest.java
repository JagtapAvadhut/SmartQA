package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.ai.AiProvider;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.DomExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FilterFormInputAssociationTest {

    @Test
    void labeledFieldInputSkipsGlobalSearchPlaceholderInSameForm() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <form>
                        <input placeholder="Search" id="global-search" />
                        <div class="grid-item">
                          <label>Username</label>
                          <input id="filter-username" class="oxd-input" />
                        </div>
                      </form>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), intelligence);
            ElementResolver.ResolvedElement resolved = resolver.resolve(page, "input", "Username");
            resolved.locator().fill("John");
            assertEquals("John", page.locator("#filter-username").inputValue());
            browser.close();
        }
    }
}
