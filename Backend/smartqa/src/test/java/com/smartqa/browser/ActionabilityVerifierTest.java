package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionabilityVerifierTest {

    @Test
    void coveredTargetFailsActionabilityWithoutForce() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body style="margin:0">
                      <button id="target">Save</button>
                      <div id="overlay" style="position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.4)"></div>
                    </body></html>
                    """);
            ActionabilityVerifier.Result result = ActionabilityVerifier.verify(page.locator("#target"), "click");
            assertTrue(result.exists());
            assertTrue(result.visible());
            assertTrue(result.covered());
            assertFalse(result.ok());
            browser.close();
        }
    }

    @Test
    void visibleEnabledTargetPasses() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body><button id='ok'>Go</button></body></html>");
            ActionabilityVerifier.Result result = ActionabilityVerifier.verify(page.locator("#ok"), "click");
            assertTrue(result.ok());
            assertEquals(true, result.visible());
            browser.close();
        }
    }

    @Test
    void detachedTargetFailsAsStale() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body><button id='gone'>Go</button></body></html>");
            Locator button = page.locator("#gone");
            assertTrue(button.count() > 0);
            page.evaluate("() => document.getElementById('gone').remove()");
            ActionabilityVerifier.Result result = ActionabilityVerifier.verify(button, "click");
            assertFalse(result.ok());
            browser.close();
        }
    }
}
