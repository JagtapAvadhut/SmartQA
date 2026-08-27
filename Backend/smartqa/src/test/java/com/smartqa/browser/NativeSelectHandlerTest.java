package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSelectHandlerTest {

    @Test
    void matchesCurrencyFormattedOptionByDigits() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <label>Min Price
                        <select id="min">
                          <option value="">Min</option>
                          <option value="10000">₹10000</option>
                          <option value="40000">₹40000</option>
                          <option value="60000">₹60000</option>
                        </select>
                      </label>
                    </body></html>
                    """);
            NativeSelectHandler.selectOption(page.locator("#min"), "40000");
            assertEquals("40000", page.locator("#min").inputValue());
            browser.close();
        }
    }

    @Test
    void matchesCommaAndCompactKAsSameLogicalValue() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <select id="min">
                        <option value="">Min</option>
                        <option value="40000">₹40,000</option>
                        <option value="60000">₹60,000</option>
                      </select>
                    </body></html>
                    """);
            NativeSelectHandler.selectOption(page.locator("#min"), "40000");
            assertEquals("40000", page.locator("#min").inputValue());
            page.locator("#min").selectOption("");
            NativeSelectHandler.selectOption(page.locator("#min"), "₹40K");
            assertEquals("40000", page.locator("#min").inputValue());
            assertEquals(40000, NativeSelectHandler.compactNumber("40000"));
            assertEquals(40000, NativeSelectHandler.compactNumber("₹40,000"));
            assertEquals(40000, NativeSelectHandler.compactNumber("40,000"));
            assertEquals(40000, NativeSelectHandler.compactNumber("₹40K"));
            browser.close();
        }
    }

    @Test
    void doesNotTreatFortyAsFortyThousand() {
        assertFalse(NativeSelectHandler.sameLogicalValue("40", "40000"));
        assertFalse(NativeSelectHandler.sameLogicalValue("40000", "40"));
        assertTrue(NativeSelectHandler.sameLogicalValue("40000", "₹40,000"));
        assertTrue(NativeSelectHandler.sameLogicalValue("HP", "HP"));
        assertFalse(NativeSelectHandler.sameLogicalValue("HP", "40000"));
    }
}
