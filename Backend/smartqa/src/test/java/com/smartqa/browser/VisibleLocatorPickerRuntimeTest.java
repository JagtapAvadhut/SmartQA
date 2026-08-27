package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleLocatorPickerRuntimeTest {

    @Test
    void firstVisibleSkipsHiddenAndHiddenCheckboxFallbackWorks() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <button style="display:none">Save</button>
                      <button id="ok">Save</button>
                      <label class="switch"><input id="hidden-box" type="checkbox" style="opacity:0;width:1px;height:1px">On</label>
                    </body></html>
                    """);
            Locator visible = VisibleLocatorPicker.firstVisible(page.getByText("Save"));
            assertNotNull(visible);
            assertEquals("ok", visible.getAttribute("id"));

            Locator hiddenNative = page.locator("#hidden-box");
            Locator control = VisibleLocatorPicker.firstVisibleOrControl(hiddenNative);
            assertNotNull(control);
            CustomToggleState.ensure(hiddenNative, true);
            assertTrue(CustomToggleState.isChecked(hiddenNative));

            Locator probed = OutcomeProbeResolver.resolve(
                    page,
                    List.of(new OutcomeProbeResolver.Probe(page.locator("#ok"), "Save")),
                    "On");
            assertNotNull(probed);
            browser.close();
        }
    }
}
