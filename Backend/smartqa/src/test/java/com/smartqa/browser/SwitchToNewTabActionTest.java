package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchToNewTabActionTest {

    @Test
    void detectsNewPageByPageSetDifferenceNotFixedIndex() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(java.util.List.of("--disable-popup-blocking")));
            var context = browser.newContext();
            Page page = context.newPage();
            page.setContent("<html><body><button id='go'>open</button></body></html>");
            NewPageTracker.Capture before = NewPageTracker.capture(page);
            AtomicReference<Page> popup = NewPageTracker.armPopupListener(page);
            page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8_000),
                    () -> page.evaluate("() => window.open('about:blank', '_blank')"));
            NewPageTracker.Result result = NewPageTracker.resolveAfterAction(page, before, popup);
            if (!result.opened()) {
                result = NewPageTracker.awaitNewPage(page, before, 3_000);
            }
            assertTrue(result.opened());
            assertNotSame(page, result.newPage());
            assertEquals(before.countBefore() + 1, result.countAfter());
            browser.close();
        }
    }
}
