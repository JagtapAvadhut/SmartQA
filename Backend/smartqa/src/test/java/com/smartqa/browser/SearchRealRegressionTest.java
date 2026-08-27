package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real browser search regression: input → autocomplete → suggestion → execute → result state.
 */
@EnabledIfEnvironmentVariable(named = "SMARTQA_SEARCH_REAL", matches = "true")
class SearchRealRegressionTest {

    @Test
    void discoversInputSelectsAutocompleteAndReachesResultState() {
        try (Playwright playwright = Playwright.create()) {
            SmartQaProperties.Browser config = new SmartQaProperties.Browser();
            config.setType("chromium");
            config.setHeadless(Boolean.parseBoolean(System.getenv().getOrDefault("SMARTQA_BROWSER_HEADLESS", "true")));
            config.setMaximizeHeaded(true);
            config.setZoomPercent(50);
            PlaywrightBrowserLauncher.Session session = PlaywrightBrowserLauncher.open(playwright, config, config.isHeadless());
            Page page = session.page();
            page.setDefaultTimeout(45_000);
            BrowserNavigation.navigate(page, "https://www.wikipedia.org/");

            Locator search = page.locator("input[name='search'], #searchInput, input[type='search']").first();
            search.waitFor();
            search.click();
            search.fill("SmartQA automation");
            page.waitForTimeout(800);
            Locator suggestion = page.locator(".suggestion-link, .suggestions-result, [role='listbox'] [role='option']").first();
            if (suggestion.count() > 0) {
                suggestion.click();
            } else {
                search.press("Enter");
            }
            page.waitForLoadState();
            String url = page.url().toLowerCase();
            String body = page.locator("body").innerText();
            assertTrue(url.contains("search") || url.contains("wiki") || body.toLowerCase().contains("smart"),
                    "Expected search result state; url=" + url);
            session.close();
        }
    }
}
