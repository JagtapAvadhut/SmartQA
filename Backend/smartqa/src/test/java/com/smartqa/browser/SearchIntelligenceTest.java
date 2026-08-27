package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIntelligenceTest {

    @Test
    void hiddenSearchFieldFailsFastWithoutWaiting() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <input type="text" id="hidden-city" style="display:none" placeholder="Enter city">
                      <button type="submit" aria-label="Search">Search</button>
                    </body></html>
                    """);
            SmartQaException ex = assertThrows(SmartQaException.class,
                    () -> SearchIntelligence.execute(page, page.locator("#hidden-city"), "phones", "https://example.com"));
            assertTrue(ex.getMessage().toLowerCase().contains("not visible")
                    || ex.getMessage().toLowerCase().contains("actionability"));
            browser.close();
        }
    }

    @Test
    void searchButtonCannotBeFilled() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <button type="submit" aria-label="Search for Products, Brands and More">Search</button>
                    </body></html>
                    """);
            SmartQaException ex = assertThrows(SmartQaException.class,
                    () -> SearchIntelligence.execute(
                            page,
                            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON),
                            "Bluetooth Speaker",
                            "https://example.com"));
            assertTrue(ex.getMessage().toLowerCase().contains("button"));
            browser.close();
        }
    }
}
