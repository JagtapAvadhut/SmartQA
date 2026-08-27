package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.ai.AiProvider;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.BrowserSnapshot;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElementResolverHealingTest {

    @Test
    void verifyKnownLocatorReturnsPresentWhenLocatorStillValid() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body><input class='username' /></body></html>");

            ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), mock(BrowserIntelligenceService.class));
            var verified = resolver.verifyKnownLocator(page, ".username", "css");
            assertTrue(verified.isPresent());
            assertFalse(verified.get().healed());
            browser.close();
        }
    }

    @Test
    void verifyKnownLocatorReturnsEmptyWhenLocatorInvalid() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body></body></html>");

            ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), mock(BrowserIntelligenceService.class));
            assertTrue(resolver.verifyKnownLocator(page, ".missing", "css").isEmpty());
            browser.close();
        }
    }

    @Test
    void healTimesOutWhenResolutionBudgetIsExceeded() {
        BrowserIntelligenceService intelligence = mock(BrowserIntelligenceService.class);
        Page page = mock(Page.class);
        when(intelligence.inspect(any(Page.class), anyList()))
                .thenReturn(new BrowserSnapshot("https://example.test", "Example", 0, List.of(), List.of()));

        ElementResolver resolver = new ElementResolver(mock(AiProvider.class), new JsonMapper(), intelligence);
        assertThrows(SmartQaException.class, () ->
                resolver.heal(page, "click", "Save", ".save", "css", Duration.ZERO));
    }
}
