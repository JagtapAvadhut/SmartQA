package com.smartqa.browser;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.ViewportSize;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaywrightBrowserLauncherTest {

    @Test
    void headedMaximizeUsesStartMaximizedAndNullViewport() {
        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setType("chromium");
        config.setHeadless(false);
        config.setMaximizeHeaded(true);

        BrowserType.LaunchOptions launch = PlaywrightBrowserLauncher.launchOptions(config, false);
        assertFalse(Boolean.TRUE.equals(launch.headless));
        assertNotNull(launch.args);
        assertTrue(launch.args.contains("--start-maximized"));

        var context = PlaywrightBrowserLauncher.contextOptions(config, false, true);
        assertNotNull(context.viewportSize);
        assertTrue(context.viewportSize.isEmpty(), "Headed maximize must clear fixed viewport emulation");
    }

    @Test
    void headlessUsesDeterministicViewportWithoutMaximizeArgs() {
        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setHeadless(true);
        config.setMaximizeHeaded(true);
        config.setHeadlessViewportWidth(1366);
        config.setHeadlessViewportHeight(768);

        BrowserType.LaunchOptions launch = PlaywrightBrowserLauncher.launchOptions(config, true);
        assertTrue(Boolean.TRUE.equals(launch.headless));
        assertFalse(containsMaximizeArg(launch.args), "Headless must not use maximize args");

        var context = PlaywrightBrowserLauncher.contextOptions(config, true, false);
        assertTrue(context.viewportSize.isPresent());
        ViewportSize size = context.viewportSize.get();
        assertEquals(1366, size.width);
        assertEquals(768, size.height);
    }

    @Test
    void headlessOverrideWinsOverConfig() {
        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setHeadless(false);
        assertTrue(PlaywrightBrowserLauncher.resolveHeadless(config, true));
        assertFalse(PlaywrightBrowserLauncher.resolveMaximize(config, true));
    }

    @Test
    void generatedSnippetEncodesSharedPolicy() {
        String snippet = PlaywrightBrowserLauncher.generatedLaunchSnippet("            ");
        assertTrue(snippet.contains("--start-maximized"));
        assertTrue(snippet.contains("setViewportSize((com.microsoft.playwright.options.ViewportSize) null)"));
        assertTrue(snippet.contains("smartqa.browser.headless"));
        assertTrue(snippet.contains("smartqa.browser.zoom-percent"));
        assertTrue(snippet.contains("ControlOrMeta+-") || snippet.contains("ControlOrMeta+0"));
        assertFalse(snippet.toLowerCase().contains("orangehrm"));
        assertFalse(snippet.toLowerCase().contains("urbancompany"));
        assertFalse(snippet.contains("HALF_SCREEN") || snippet.contains("screenWidth * 0.50"));
    }

    @Test
    void transientNetworkChangeIsRetryableWarmupFailure() {
        assertTrue(PlaywrightBrowserLauncher.isTransientNavigationFailure(
                "net::ERR_NETWORK_CHANGED at https://example.com/"));
        assertTrue(PlaywrightBrowserLauncher.isTransientNavigationFailure("Timeout 15000ms exceeded"));
        assertFalse(PlaywrightBrowserLauncher.isTransientNavigationFailure("Target page, context or browser has been closed"));
    }

    @Test
    void headedZoomDefaultsToFifty() {
        SmartQaProperties.Browser config = new SmartQaProperties.Browser();
        config.setHeadless(false);
        assertEquals(50, BrowserPageZoom.resolveZoomPercent(config, false));
        assertEquals(100, BrowserPageZoom.resolveZoomPercent(config, true));
    }

    private static boolean containsMaximizeArg(java.util.List<String> args) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        return args.stream().anyMatch(arg -> arg.contains("start-maximized") || arg.contains("start-maximized"));
    }
}
