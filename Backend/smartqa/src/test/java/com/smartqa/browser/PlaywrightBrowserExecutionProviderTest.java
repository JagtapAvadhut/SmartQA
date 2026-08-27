package com.smartqa.browser;

import com.microsoft.playwright.BrowserType;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaywrightBrowserExecutionProviderTest {

    @Test
    void launchOptionsUsesHeadlessTrueOverride() {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getBrowser().setHeadless(false);
        PlaywrightBrowserExecutionProvider provider = new PlaywrightBrowserExecutionProvider(
                null, null, properties, null, null);

        BrowserType.LaunchOptions options = provider.launchOptions(new BrowserExecutionOptions("playwright", true));

        assertTrue(options.headless);
        assertTrue(options.args == null || options.args.stream().noneMatch(arg ->
                arg.contains("start-maximized") || arg.contains("start-maximized")));
    }

    @Test
    void launchOptionsUsesHeadlessFalseOverrideWithMaximize() {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getBrowser().setHeadless(true);
        properties.getBrowser().setMaximizeHeaded(true);
        properties.getBrowser().setType("chromium");
        PlaywrightBrowserExecutionProvider provider = new PlaywrightBrowserExecutionProvider(
                null, null, properties, null, null);

        BrowserType.LaunchOptions options = provider.launchOptions(new BrowserExecutionOptions("playwright", false));

        assertFalse(options.headless);
        assertNotNull(options.args);
        assertTrue(options.args.contains("--start-maximized"));
    }
}
