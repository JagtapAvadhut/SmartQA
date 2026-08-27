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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationStateMismatchTest {

    @Test
    void requestedMumbaiAndActualNagpurIsMismatch() {
        assertFalse(SearchStateContract.containsDistinctiveTokens("Mumbai", "Nagpur"));
        assertTrue(SearchStateContract.containsDistinctiveTokens("Mumbai", "Double screen mobile phone near Mumbai"));
    }

    @Test
    void livePageWithWrongCityFailsLocationContract() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <input id="city" placeholder="Enter city" value="Nagpur" />
                      <h1>Results near Nagpur</h1>
                    </body></html>
                    """);
            SmartQaException ex = assertThrows(SmartQaException.class,
                    () -> SearchStateContract.verifyLocation(page, "Mumbai"));
            assertEquals(ErrorCode.LOCATION_STATE_MISMATCH, ex.errorCode());
            browser.close();
        }
    }

    @Test
    void requestedAndVerifiedStaySeparateOnMismatch() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <input id="city" placeholder="Enter city" value="Nagpur" />
                      <h1>Results near Nagpur</h1>
                    </body></html>
                    """);
            SearchStateContract.begin();
            try {
                assertThrows(SmartQaException.class, () -> SearchStateContract.verifyLocation(page, "Mumbai"));
                LocationState state = SearchStateContract.current().locationState();
                assertEquals("Mumbai", state.requested());
                assertNotEquals("Mumbai", state.verified());
                assertEquals(SearchState.VerificationStatus.MISMATCH, state.verificationStatus());
                assertThrows(SmartQaException.class, () -> SearchStateContract.verifyReadyForFilter(page));
            } finally {
                SearchStateContract.end();
            }
            browser.close();
        }
    }
}
