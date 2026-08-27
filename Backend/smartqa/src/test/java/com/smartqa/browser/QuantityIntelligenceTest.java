package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityIntelligenceTest {

    @Test
    void clickWithoutQuantityChangeIsMismatch() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <span>Qty 1</span>
                      <div>Total ₹100</div>
                      <button id="plus">+</button>
                    </body></html>
                    """);
            QuantityIntelligence.Snapshot before = QuantityIntelligence.capture(page);
            page.locator("#plus").click();
            SmartQaException ex = assertThrows(SmartQaException.class,
                    () -> QuantityIntelligence.ensureIncremented(page, before));
            assertEquals(ErrorCode.QUANTITY_STATE_MISMATCH, ex.errorCode());
            browser.close();
        }
    }
}
