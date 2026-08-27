package com.smartqa.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SMARTQA_UI_SMOKE", matches = "true")
class WorkspaceUiSmokeTest {

    @Test
    void workspaceShowsUrlInstructionsLoadExampleAndAnalyze() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate("http://localhost:5300/");
            Locator urlInput = page.getByPlaceholder("https://example.com");
            Locator instructions = page.getByPlaceholder("Describe what you want SmartQA to test...");
            urlInput.waitFor();
            assertTrue(urlInput.isVisible());
            assertTrue(instructions.isVisible());
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Load Example")).first().click();
            String url = urlInput.inputValue();
            String text = instructions.inputValue();
            assertTrue(url.contains("opensource-demo.orangehrmlive.com"), url);
            assertTrue(text.contains("Username"), text);
            assertTrue(text.contains("Passwords do not match"), text);
            Locator analyze = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Analyze Test")).first();
            assertTrue(analyze.isEnabled());
            analyze.click();
            page.getByText("Analyzing test").first().waitFor();
            Locator understanding = page.locator("article").filter(new Locator.FilterOptions().setHasText("Test understanding"));
            Locator statusBadge = understanding.locator(".badge");
            statusBadge.waitFor(new Locator.WaitForOptions().setTimeout(180_000));
            String status = statusBadge.innerText();
            assertTrue(status.contains("READY") || status.contains("NEEDS_CLARIFICATION"), status);
            browser.close();
        }
    }
}
