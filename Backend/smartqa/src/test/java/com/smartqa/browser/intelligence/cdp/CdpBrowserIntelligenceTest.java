package com.smartqa.browser.intelligence.cdp;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CdpBrowserIntelligenceTest {

    @Test
    void chromiumSessionCapturesRealDomSnapshot() {
        SmartQaProperties properties = new SmartQaProperties();
        CdpBrowserIntelligence intelligence = new CdpBrowserIntelligence(properties);
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body><h3>Brand</h3><label><input type='checkbox'> AK</label></body></html>");
            CdpCapture capture = intelligence.capture(page);
            assertTrue(capture.captured(), "Chromium CDP snapshot must actually succeed");
            assertTrue(capture.nodeCount() > 0, "CDP documents must contain nodes");
            assertTrue(capture.graph().findByText("AK", 8).size() + capture.compactAccessibility(20).length() > 0);
            browser.close();
        }
    }
}
