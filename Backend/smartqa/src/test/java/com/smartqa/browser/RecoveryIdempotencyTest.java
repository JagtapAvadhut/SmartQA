package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.pipeline.FailureEvidence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryIdempotencyTest {

    @Test
    void skipsFilterRetryWhenOptionAlreadyChecked() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <label><input type="checkbox" checked value="AK"> AK</label>
                    </body></html>
                    """);
            FailureEvidence evidence = FailureEvidence.builder()
                    .expected("AK")
                    .actual("AK")
                    .build();
            assertTrue(RecoveryIdempotency.alreadySatisfied(page, "RE_APPLY_FILTER", evidence));
            browser.close();
        }
    }
}
