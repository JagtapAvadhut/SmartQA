package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic DOM only — no site URLs. Covers generic overlay intercept recovery.
 */
class BlockingOverlayGuardTest {

    @Test
    void safeClickDismissesBlockingOverlayThenClicksTarget() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body style="margin:0">
                      <button id="target">Save</button>
                      <div id="overlay" class="modal-overlay" style="position:fixed;inset:0;z-index:9999;
                           background:rgba(0,0,0,.45)">
                        <button id="close" aria-label="Close" style="position:absolute;top:8px;right:8px">Close</button>
                      </div>
                      <script>
                        document.getElementById('close').onclick = () => {
                          document.getElementById('overlay').remove();
                        };
                        document.getElementById('target').onclick = () => {
                          document.body.setAttribute('data-clicked', '1');
                        };
                      </script>
                    </body></html>
                    """);
            long started = System.nanoTime();
            SafeClick.click(page.locator("#target"), page);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertEquals("1", page.locator("body").getAttribute("data-clicked"));
            assertTrue(elapsedMs < 20_000, "must not burn full 30s click timeout, elapsed=" + elapsedMs);
            browser.close();
        }
    }

    @Test
    void safeClickPromotesAriaHiddenIconToParentButton() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <button id="host">
                        Product Card
                        <span><i id="icon" aria-hidden="true" class="sellericonsD"></i></span>
                      </button>
                      <script>
                        document.getElementById('host').onclick = () => {
                          document.body.setAttribute('data-clicked', 'host');
                        };
                      </script>
                    </body></html>
                    """);
            SafeClick.click(page.locator("#icon"), page);
            assertEquals("host", page.locator("body").getAttribute("data-clicked"));
            browser.close();
        }
    }

    @Test
    void dismissesConsentAcceptButtonCoveringTarget() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body style="margin:0">
                      <button id="target">Location</button>
                      <div id="cookie" class="cookie-banner" role="dialog"
                           style="position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.5);
                                  display:flex;align-items:center;justify-content:center">
                        <button id="accept">Accept all</button>
                      </div>
                      <script>
                        document.getElementById('accept').onclick = () => {
                          document.getElementById('cookie').remove();
                        };
                        document.getElementById('target').onclick = () => {
                          document.body.setAttribute('data-clicked', '1');
                        };
                      </script>
                    </body></html>
                    """);
            assertTrue(BlockingOverlayGuard.dismissConsentBanners(page)
                    || BlockingOverlayGuard.dismissCoveringElement(page, page.locator("#target")));
            SafeClick.click(page.locator("#target"), page);
            assertEquals("1", page.locator("body").getAttribute("data-clicked"));
            browser.close();
        }
    }

    @Test
    void escapeDismissesAriaModalOverlay() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body style="margin:0">
                      <div id="overlay" role="dialog" aria-modal="true"
                           style="position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.4)"></div>
                      <script>
                        document.addEventListener('keydown', (e) => {
                          if (e.key === 'Escape') document.getElementById('overlay')?.remove();
                        });
                      </script>
                    </body></html>
                    """);
            assertTrue(BlockingOverlayGuard.dismissIfBlocking(page));
            assertEquals(0, page.locator("#overlay").count());
            browser.close();
        }
    }
}
