package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies headed Chromium page zoom is ≈50% (Chrome Zoom UI), while the OS window stays maximized —
 * not a half-desktop window resize.
 */
class PageZoomRegressionTest {

    @Test
    void headedPageZoomIsFiftyPercentWhileWindowStaysLarge() throws Exception {
        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setType("chromium");
        config.setHeadless(false);
        config.setMaximizeHeaded(true);
        config.setZoomPercent(50);

        Path screenshot = Files.createTempFile("smartqa-page-zoom-", ".png");
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = PlaywrightBrowserLauncher.open(playwright, config, false);
            Page page = session.page();
            page.navigate("https://example.com");
            BrowserPageZoom.ZoomEvidence zoom = BrowserPageZoom.apply(page, 50);
            BrowserViewportEvidence viewport = PlaywrightBrowserLauncher.captureViewport(
                    page, "chromium", false, true);
            page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(false));

            System.out.printf(
                    "PAGE_ZOOM_REGRESSION requested=%s effective=%.1f%% method=%s window=%dx%d viewport=%dx%d dpr=%s url=%s screenshot=%s%n",
                    zoom.requestedZoomPercent(),
                    zoom.effectiveZoomPercent(),
                    zoom.method(),
                    zoom.browserWindowWidth(), zoom.browserWindowHeight(),
                    zoom.viewportWidth(), zoom.viewportHeight(),
                    zoom.devicePixelRatio(),
                    zoom.pageUrl(),
                    screenshot.toAbsolutePath());

            assertEquals(50, zoom.requestedZoomPercent());
            assertTrue(zoom.approximatelyRequested(),
                    "Effective page zoom must be ≈50%, got " + zoom.effectiveZoomPercent()
                            + " via " + zoom.method());
            assertTrue(BrowserPageZoom.isApproximately(50, zoom.effectiveZoomPercent()));
            assertTrue("chrome.tabs.setZoom".equals(zoom.method()) || zoom.approximatelyRequested(),
                    "Prefer chrome.tabs.setZoom so Chrome Zoom UI shows 50%");

            // Window must remain a normal/large headed window — not a deliberate half-desktop resize policy.
            assertTrue(viewport.outerWidth() >= (int) (viewport.availableScreenWidth() * 0.70),
                    "Browser window should stay large/maximized, outerWidth=" + viewport.outerWidth()
                            + " avail=" + viewport.availableScreenWidth());
            assertTrue(zoom.viewportWidth() > 0 && zoom.viewportHeight() > 0,
                    "Viewport must remain usable at 50% page zoom");

            // DOM still works under zoom.
            assertTrue(page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING).first().isVisible());
            page.locator("body").click();
            session.close();
        } finally {
            Files.deleteIfExists(screenshot);
        }
    }
}
