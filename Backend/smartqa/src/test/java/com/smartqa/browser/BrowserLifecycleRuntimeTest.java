package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserLifecycleRuntimeTest {

    @Test
    void pageStaysAliveAcrossMultipleEvaluates() {
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = openHeadless(playwright);
            Page page = session.page();
            for (int i = 0; i < 5; i++) {
                Object result = page.evaluate("() => 1");
                assertEquals(1, ((Number) result).intValue());
                assertTrue(PlaywrightBrowserLauncher.isPageAlive(page));
            }
            PlaywrightBrowserLauncher.closeQuietly(session, BrowserLifecycle.CLOSE_EXECUTION_COMPLETE,
                    "BrowserLifecycleRuntimeTest.pageStaysAliveAcrossMultipleEvaluates");
            assertFalse(PlaywrightBrowserLauncher.isPageAlive(page));
        }
    }

    @Test
    void pageCloseEventIsClassifiedSeparatelyFromBrowser() {
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = openHeadless(playwright);
            Page page = session.page();
            BrowserContext context = session.context();
            Browser browser = session.browser();
            page.close();
            assertEquals(BrowserLifecycle.PAGE_CLOSED, BrowserLifecycle.classifyClosedResource(page, context, browser));
            assertFalse(PlaywrightBrowserLauncher.isPageAlive(page));
            PlaywrightBrowserLauncher.closeQuietly(session, BrowserLifecycle.CLOSE_TARGET_CLOSED,
                    "BrowserLifecycleRuntimeTest.pageCloseEventIsClassifiedSeparatelyFromBrowser");
        }
    }

    @Test
    void contextCloseEventIsClassified() {
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = openHeadless(playwright);
            Page page = session.page();
            BrowserContext context = session.context();
            Browser browser = session.browser();
            context.close();
            String classified = BrowserLifecycle.classifyClosedResource(page, context, browser);
            assertTrue(BrowserLifecycle.CONTEXT_CLOSED.equals(classified) || BrowserLifecycle.PAGE_CLOSED.equals(classified));
            assertFalse(PlaywrightBrowserLauncher.isPageAlive(page));
            PlaywrightBrowserLauncher.closeQuietly(session, BrowserLifecycle.CLOSE_TARGET_CLOSED,
                    "BrowserLifecycleRuntimeTest.contextCloseEventIsClassified");
        }
    }

    @Test
    void browserCloseEventIsClassified() {
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = openHeadless(playwright);
            Page page = session.page();
            BrowserContext context = session.context();
            Browser browser = session.browser();
            browser.close();
            assertEquals(BrowserLifecycle.BROWSER_CLOSED, BrowserLifecycle.classifyClosedResource(page, context, browser));
            PlaywrightBrowserLauncher.closeQuietly(session, BrowserLifecycle.CLOSE_TARGET_CLOSED,
                    "BrowserLifecycleRuntimeTest.browserCloseEventIsClassified");
        }
    }

    @Test
    void generationPlaywrightDisposeDoesNotCloseSeparateExecutionBrowser() {
        try (Playwright generation = Playwright.create(); Playwright execution = Playwright.create()) {
            PlaywrightBrowserLauncher.Session generationSession = openHeadless(generation);
            PlaywrightBrowserLauncher.Session executionSession = openHeadless(execution);
            assertTrue(PlaywrightBrowserLauncher.isPageAlive(generationSession.page()));
            assertTrue(PlaywrightBrowserLauncher.isPageAlive(executionSession.page()));
            PlaywrightBrowserLauncher.closeQuietly(generationSession, BrowserLifecycle.CLOSE_EXECUTION_COMPLETE,
                    "generation-complete");
            assertTrue(PlaywrightBrowserLauncher.isPageAlive(executionSession.page()),
                    "Execution browser must survive generation completion");
            Object result = executionSession.page().evaluate("() => 7");
            assertEquals(7, ((Number) result).intValue());
            PlaywrightBrowserLauncher.closeQuietly(executionSession, BrowserLifecycle.CLOSE_EXECUTION_COMPLETE,
                    "execution-complete");
        }
    }

    @Test
    void concurrentSessionsDoNotCloseEachOther() {
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session first = openHeadless(playwright);
            PlaywrightBrowserLauncher.Session second = openHeadless(playwright);
            PlaywrightBrowserLauncher.closeQuietly(first, BrowserLifecycle.CLOSE_EXECUTION_COMPLETE, "run-a");
            assertTrue(PlaywrightBrowserLauncher.isPageAlive(second.page()));
            PlaywrightBrowserLauncher.closeQuietly(second, BrowserLifecycle.CLOSE_EXECUTION_COMPLETE, "run-b");
        }
    }

    @Test
    void closedPageIsNotReusedForRecovery() {
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = openHeadless(playwright);
            Page page = session.page();
            page.close();
            assertFalse(PlaywrightBrowserLauncher.isPageAlive(page));
            PlaywrightBrowserLauncher.closeQuietly(session, BrowserLifecycle.CLOSE_TARGET_CLOSED, "recovery-skip");
        }
    }

    @Test
    void applyZoomOnClosedPageDoesNotThrow() {
        try (Playwright playwright = Playwright.create()) {
            PlaywrightBrowserLauncher.Session session = openHeadless(playwright);
            Page page = session.page();
            page.close();
            BrowserPageZoom.ZoomEvidence evidence = BrowserPageZoom.apply(page, 50);
            assertEquals("page-closed", evidence.method());
            PlaywrightBrowserLauncher.closeQuietly(session, BrowserLifecycle.CLOSE_TARGET_CLOSED, "zoom-closed");
        }
    }

    private static PlaywrightBrowserLauncher.Session openHeadless(Playwright playwright) {
        com.smartqa.common.config.SmartQaProperties.Browser config = new com.smartqa.common.config.SmartQaProperties.Browser();
        config.setType("chromium");
        config.setHeadless(true);
        config.setZoomPercent(100);
        return PlaywrightBrowserLauncher.open(playwright, config, true);
    }
}

