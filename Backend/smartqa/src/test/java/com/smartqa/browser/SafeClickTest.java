package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeClickTest {

    @Test
    void clickReturnsEvenWhenFormNavigationNeverSettles() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.route("**/hang-forever", route -> {
                // Intentionally never fulfill — simulates a login post that never settles.
            });
            page.setContent("""
                    <html><body>
                      <form action="/hang-forever" method="get">
                        <button type="submit">Login</button>
                      </form>
                    </body></html>
                    """);
            long started = System.nanoTime();
            SafeClick.click(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Login")), page);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertTrue(elapsedMs < 20_000,
                    "SafeClick must not wait 30s for a hanging navigation, elapsed=" + elapsedMs);
            browser.close();
        }
    }

    @Test
    void clickDoesNotFailWhenNavigationDestroysExecutionContext() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <a id="go" href="about:blank">Go</a>
                    </body></html>
                    """);
            SafeClick.click(page.locator("#go"), page);
            String title = "";
            try {
                title = page.title();
            } catch (RuntimeException ex) {
                throw new AssertionError("page.title() must be readable after SafeClick navigation", ex);
            }
            assertTrue(title != null);
            browser.close();
        }
    }
}
