package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomIntelligencePlaywrightTest {

    @Test
    void extractsInteractiveDomAndRanksSearchAndBrandFilter() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <input placeholder="Search" name="q" data-testid="search" />
                      <aside>
                        <h3>Brand</h3>
                        <label><input type="checkbox" data-testid="brand-hp" /> HP</label>
                        <label><input type="checkbox" data-testid="brand-dell" /> Dell</label>
                      </aside>
                      <a href="/more">Learn more</a>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            BrowserSnapshot snapshot = intelligence.inspect(page, List.of());
            assertTrue(snapshot.interactiveCount() >= 3);
            LocatorRanker.RankedElement search = intelligence.resolveFromDom(snapshot, "input", "Search");
            assertNotNull(search);
            assertFalse(search.locators().isEmpty());
            LocatorRanker.RankedElement brand = intelligence.resolveFromDom(snapshot, "click", "HP");
            assertNotNull(brand);
            assertTrue(brand.element().text().contains("HP") || brand.element().accessibleName().contains("HP"));
            browser.close();
        }
    }
}
