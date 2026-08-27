package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Generic local page that switches layout by viewport width.
 * Proves headed maximize gets a desktop-capable content viewport.
 */
class ResponsiveViewportRegressionTest {

    @Test
    void headedMaximizedSeesDesktopNavigation() throws Exception {
        Path html = Files.createTempFile("smartqa-responsive-", ".html");
        Files.writeString(html, """
                <!DOCTYPE html>
                <html><head>
                  <meta charset="utf-8"/>
                  <title>Responsive Layout Fixture</title>
                  <style>
                    .desktop-nav { display: none; }
                    .hamburger { display: none; }
                    @media (max-width: 900px) {
                      .hamburger { display: block; }
                    }
                    @media (min-width: 901px) {
                      .desktop-nav { display: block; }
                    }
                  </style>
                </head><body>
                  <nav class="desktop-nav" data-testid="desktop-nav">Desktop Nav</nav>
                  <button class="hamburger" data-testid="hamburger">Menu</button>
                </body></html>
                """);

        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setType("chromium");
        config.setHeadless(false);
        config.setMaximizeHeaded(true);

        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = PlaywrightBrowserLauncher.open(playwright, config, false);
            Page page = session.page();
            page.navigate(html.toUri().toString());
            BrowserViewportEvidence evidence = PlaywrightBrowserLauncher.captureViewport(
                    page, "chromium", false, true);
            PlaywrightBrowserLauncher.emitViewportReady(evidence);

            System.out.printf(
                    "HEADED_VIEWPORT maximize=%s inner=%dx%d outer=%dx%d screen=%dx%d avail=%dx%d dpr=%s%n",
                    evidence.maximizeRequested(),
                    evidence.innerWidth(), evidence.innerHeight(),
                    evidence.outerWidth(), evidence.outerHeight(),
                    evidence.screenWidth(), evidence.screenHeight(),
                    evidence.availableScreenWidth(), evidence.availableScreenHeight(),
                    evidence.devicePixelRatio());

            assertTrue(evidence.maximizeRequested(), "Maximize must be requested in headed mode");
            assertTrue(evidence.innerWidth() >= 901,
                    "Headed content viewport must be desktop-capable, innerWidth=" + evidence.innerWidth());
            assertTrue(evidence.innerWidth() >= (int) (evidence.availableScreenWidth() * 0.80),
                    "innerWidth should use most of available screen width");
            assertTrue(page.locator("[data-testid='desktop-nav']").isVisible(),
                    "Desktop navigation must be visible at maximized viewport");
            assertFalse(page.locator("[data-testid='hamburger']").isVisible(),
                    "Hamburger must stay hidden at desktop viewport");
            session.close();
        } finally {
            Files.deleteIfExists(html);
        }
    }

    @Test
    void headlessUsesConfiguredViewportAndSeesDesktopAt1280() throws Exception {
        Path html = Files.createTempFile("smartqa-responsive-hl-", ".html");
        Files.writeString(html, """
                <!DOCTYPE html>
                <html><head>
                  <meta charset="utf-8"/>
                  <title>Responsive Layout Fixture</title>
                  <style>
                    .desktop-nav { display: none; }
                    .hamburger { display: none; }
                    @media (max-width: 900px) {
                      .hamburger { display: block; }
                    }
                    @media (min-width: 901px) {
                      .desktop-nav { display: block; }
                    }
                  </style>
                </head><body>
                  <nav class="desktop-nav" data-testid="desktop-nav">Desktop Nav</nav>
                  <button class="hamburger" data-testid="hamburger">Menu</button>
                </body></html>
                """);

        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setType("chromium");
        config.setHeadless(true);
        config.setHeadlessViewportWidth(1280);
        config.setHeadlessViewportHeight(720);

        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = PlaywrightBrowserLauncher.open(playwright, config, true);
            Page page = session.page();
            page.navigate(html.toUri().toString());
            BrowserViewportEvidence evidence = session.viewport();

            System.out.printf("HEADLESS_VIEWPORT inner=%dx%d%n", evidence.innerWidth(), evidence.innerHeight());
            assertFalse(evidence.maximizeRequested());
            assertTrue(evidence.innerWidth() == 1280, "Headless must keep deterministic width");
            assertTrue(evidence.innerHeight() == 720, "Headless must keep deterministic height");
            assertTrue(page.locator("[data-testid='desktop-nav']").isVisible());
            session.close();
        } finally {
            Files.deleteIfExists(html);
        }
    }

    @Test
    void narrowHeadlessViewportShowsHamburger() throws Exception {
        Path html = Files.createTempFile("smartqa-responsive-narrow-", ".html");
        Files.writeString(html, """
                <!DOCTYPE html>
                <html><head>
                  <meta charset="utf-8"/>
                  <title>Responsive Layout Fixture</title>
                  <style>
                    .desktop-nav { display: none; }
                    .hamburger { display: none; }
                    @media (max-width: 900px) {
                      .hamburger { display: block; }
                    }
                    @media (min-width: 901px) {
                      .desktop-nav { display: block; }
                    }
                  </style>
                </head><body>
                  <nav class="desktop-nav" data-testid="desktop-nav">Desktop Nav</nav>
                  <button class="hamburger" data-testid="hamburger">Menu</button>
                </body></html>
                """);

        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setHeadless(true);
        config.setHeadlessViewportWidth(800);
        config.setHeadlessViewportHeight(600);

        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = PlaywrightBrowserLauncher.open(playwright, config, true);
            Page page = session.page();
            page.navigate(html.toUri().toString());
            assertTrue(page.locator("[data-testid='hamburger']").isVisible());
            assertFalse(page.locator("[data-testid='desktop-nav']").isVisible());
            session.close();
        } finally {
            Files.deleteIfExists(html);
        }
    }
}
