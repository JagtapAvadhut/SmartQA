package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthSubmitLocatorTest {

    @Test
    void loginClickPrefersSubmitButtonOverHeadingWithSameText() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <h5>Login</h5>
                      <form id="auth">
                        <label>Username</label><input id="user">
                        <label>Password</label><input type="password" id="pass">
                        <button type="submit" id="login-btn">Login</button>
                      </form>
                    </body></html>
                    """);
            var locator = PlaywrightBrowserExecutionProvider.refreshCompactAuthLocator(page, "Login");
            assertEquals("login-btn", locator.getAttribute("id"));
            assertEquals("BUTTON", locator.evaluate("el => el.tagName").toString());
            browser.close();
        }
    }
}
