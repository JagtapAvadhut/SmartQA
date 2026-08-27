package com.smartqa.browser;

import com.microsoft.playwright.Playwright;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserModeSmokeTest {

    @Test
    void headlessTrueSmoke() {
        runSmoke(true);
    }

    @Test
    void headlessFalseSmoke() {
        runSmoke(false);
    }

    private void runSmoke(boolean headless) {
        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setType("chromium");
        config.setHeadless(headless);
        config.setMaximizeHeaded(true);
        config.setZoomPercent(50);
        config.setHeadlessViewportWidth(1280);
        config.setHeadlessViewportHeight(720);

        try (Playwright playwright = Playwright.create()) {
            long started = System.currentTimeMillis();
            PlaywrightBrowserLauncher.Session session =
                    PlaywrightBrowserLauncher.open(playwright, config, headless);
            var page = session.page();
            page.navigate("https://example.com");
            BrowserPageZoom.ZoomEvidence zoom = headless
                    ? session.zoom()
                    : BrowserPageZoom.apply(page, 50);
            BrowserViewportEvidence evidence = PlaywrightBrowserLauncher.captureViewport(
                    page, "chromium", session.headless(), session.maximizeRequested());
            PlaywrightBrowserLauncher.emitViewportReady(evidence);

            String url = page.url();
            String title = page.title();
            page.locator("body").click();
            String finalUrl = page.url();
            session.close();
            long duration = System.currentTimeMillis() - started;
            System.out.printf(
                    "SMOKE headless=%s maximize=%s zoomReq=%s zoomEff=%.1f%% inner=%dx%d outer=%dx%d url=%s title=%s finalUrl=%s durationMs=%d%n",
                    headless,
                    evidence.maximizeRequested(),
                    zoom == null ? -1 : zoom.requestedZoomPercent(),
                    zoom == null ? -1.0 : zoom.effectiveZoomPercent(),
                    evidence.innerWidth(),
                    evidence.innerHeight(),
                    evidence.outerWidth(),
                    evidence.outerHeight(),
                    url,
                    title,
                    finalUrl,
                    duration);
            assertTrue(title.toLowerCase().contains("example"), "Expected example title");
            assertTrue(finalUrl.startsWith("https://"), "Expected HTTPS navigation");
            if (headless) {
                assertTrue(evidence.innerWidth() == 1280 && evidence.innerHeight() == 720);
                assertEquals(100, session.zoomPercent());
            } else {
                assertTrue(evidence.maximizeRequested());
                assertEquals(50, session.zoomPercent());
                assertTrue(zoom.approximatelyRequested(),
                        "Headed smoke must apply ≈50% Chrome page zoom, effective=" + zoom.effectiveZoomPercent());
                assertTrue(evidence.outerWidth() >= (int) (evidence.availableScreenWidth() * 0.70),
                        "Window must stay large; zoom is page zoom not window resize");
                assertTrue(evidence.innerWidth() >= 901, "Headed smoke viewport must stay usable");
            }
        }
    }
}
