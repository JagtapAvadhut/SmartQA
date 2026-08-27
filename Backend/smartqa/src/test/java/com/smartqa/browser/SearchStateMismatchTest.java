package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchStateMismatchTest {

    @Test
    void requestedSamsungAndActualMicromaxIsMismatch() {
        assertFalse(SearchStateContract.containsDistinctiveTokens(
                "Samsung Smartphone",
                "Micromax smartphones near Nagpur"));
        assertTrue(SearchStateContract.containsDistinctiveTokens(
                "Samsung Smartphone",
                "Samsung Smartphone near Mumbai"));
    }

    @Test
    void livePageWithWrongResultFailsSearchContract() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <input id="q" value="Micromax" />
                      <h1>Micromax smartphones near Nagpur</h1>
                    </body></html>
                    """);
            SmartQaException ex = assertThrows(SmartQaException.class,
                    () -> SearchStateContract.verifySearch(page, "Samsung Smartphone"));
            assertEquals(ErrorCode.SEARCH_STATE_MISMATCH, ex.errorCode());
            browser.close();
        }
    }

    @Test
    void genericQueryDoesNotPassOnMarketingChrome() {
        assertFalse(SearchStateContract.containsDistinctiveTokens(
                "smartphones",
                "https://www.example.com/ India's Largest Online B2B Marketplace"));
        assertTrue(SearchStateContract.containsDistinctiveTokens(
                "smartphones",
                "https://dir.example.com/search.mp?ss=smartphones"));
        assertTrue(SearchStateContract.conflicts("Samsung Smartphone", "Micromax Smart Phone"));
    }
}
