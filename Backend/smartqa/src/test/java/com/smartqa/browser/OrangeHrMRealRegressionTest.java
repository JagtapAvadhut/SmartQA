package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SMARTQA_ORANGEHRM_REAL", matches = "true")
class OrangeHrMRealRegressionTest {

    private static final String APP_URL = "https://opensource-demo.orangehrmlive.com/";

    @Test
    void executesRealOrangeHrMScenario() {
        try (Playwright playwright = Playwright.create()) {
            SmartQaProperties.Browser config = new SmartQaProperties.Browser();
            config.setType("chromium");
            config.setHeadless(false);
            config.setMaximizeHeaded(true);
            config.setZoomPercent(50);
            PlaywrightBrowserLauncher.Session session = PlaywrightBrowserLauncher.open(playwright, config, false);
            Page page = session.page();
            page.setDefaultTimeout(60_000);
            BrowserNavigation.navigate(page, APP_URL);
            BrowserPageZoom.ZoomEvidence zoom = BrowserPageZoom.apply(page, 50);
            System.out.printf(
                    "ORANGEHRM_PAGE_ZOOM requested=%s effective=%.1f%% window=%dx%d viewport=%dx%d%n",
                    zoom.requestedZoomPercent(), zoom.effectiveZoomPercent(),
                    zoom.browserWindowWidth(), zoom.browserWindowHeight(),
                    zoom.viewportWidth(), zoom.viewportHeight());
            assertTrue(zoom.approximatelyRequested(), "OrangeHRM headed run must use 50% page zoom");

            runStep(page, 1, "input", "Username", "role:textbox[name=Username]", 0.96, () ->
                    page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username")).fill("Admin"));
            runStep(page, 2, "input", "Password", "role:textbox[name=Password]", 0.96, () ->
                    page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).fill("admin123"));
            runStep(page, 3, "click", "Login", "role:button[name=Login]", 0.98, () ->
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click());
            runStep(page, 4, "click", "Search", "placeholder:Search", 0.90, () ->
                    page.getByPlaceholder("Search").first().click());
            runStep(page, 5, "input", "Search value", "placeholder:Search", 0.90, () ->
                    page.getByPlaceholder("Search").first().fill("Admin"));
            runStep(page, 6, "click", "Admin", "role:link[name=Admin]", 0.88, () ->
                    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Admin")).first().click());
            runStep(page, 7, "click", "Add", "role:button[name=Add]", 0.90, () ->
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add")).first().click());
            runStep(page, 8, "click", "User Role dropdown", "text:-- Select --", 0.82, () ->
                    page.getByText("-- Select --", new Page.GetByTextOptions().setExact(true)).first().click());
            runStep(page, 9, "click", "ESS option", "text:ESS", 0.80, () ->
                    page.getByText("ESS", new Page.GetByTextOptions().setExact(true)).first().click());
            runStep(page, 10, "input", "Employee Name", "placeholder:Type for hints...", 0.90, () -> {
                Locator employee = page.getByPlaceholder("Type for hints...").first();
                employee.fill("Radha Gupta");
                Locator suggestion = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("Radha Gupta"));
                if (suggestion.count() == 0) {
                    suggestion = page.locator("[role='listbox'] >> text=Radha Gupta");
                }
                suggestion.first().waitFor(new Locator.WaitForOptions().setTimeout(10_000));
                suggestion.first().click();
            });
            runStep(page, 11, "input", "Password", "input[type=password]", 0.82, () ->
                    page.locator("input[type='password']").nth(0).fill("Demo@12345"));
            runStep(page, 12, "input", "Confirm Password", "input[type=password]", 0.82, () ->
                    page.locator("input[type='password']").nth(1).fill("Mismatch@12345"));
            runStep(page, 13, "click", "Save", "role:button[name=Save]", 0.90, () ->
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).last().click());
            runStep(page, 14, "verify", "Passwords do not match", "text:Passwords do not match", 0.95, () -> {
                Locator warning = page.getByText("Passwords do not match");
                warning.first().waitFor(new Locator.WaitForOptions().setTimeout(15000));
                assertTrue(warning.first().isVisible(), "Expected validation message to be visible");
                String actualText = warning.first().innerText().trim();
                assertEquals("Passwords do not match", actualText);
                emitAssertionEvidence(page, warning, actualText);
            });
            session.close();
        }
    }

    private static void emitAssertionEvidence(Page page, Locator element, String actualText) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("expectedText", "Passwords do not match");
        evidence.put("actualText", actualText);
        evidence.put("visible", element.first().isVisible());
        evidence.put("enabled", element.first().isEnabled());
        evidence.put("url", page.url());
        evidence.put("pageTitle", page.title());
        evidence.put("frameContext", "main");
        evidence.put("locator", "text:Passwords do not match");
        evidence.put("locatorType", "text");
        evidence.put("confidence", 0.95);
        evidence.put("timestamp", Instant.now().toString());
        System.out.println("ORANGEHRM_ASSERTION " + evidence);
    }

    private static void runStep(Page page, int stepNumber, String action, String target, String locator,
                                double confidence, Runnable runnable) {
        long started = System.nanoTime();
        String result = "PASS";
        String errorCategory = "";
        String exception = "";
        String screenshotPath = "";
        String domEvidence = "";
        try {
            runnable.run();
            domEvidence = safeDomEvidence(page, target);
        } catch (RuntimeException ex) {
            result = "FAIL";
            errorCategory = "ACTION_FAILURE";
            exception = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            try {
                Path shot = Path.of("target", "orangehrm-failure-step-" + stepNumber + "-" + Instant.now().toEpochMilli() + ".png");
                page.screenshot(new Page.ScreenshotOptions().setPath(shot).setFullPage(true));
                screenshotPath = shot.toString();
            } catch (RuntimeException ignored) {
            }
            domEvidence = safeDomEvidence(page, target);
            emitStep(stepNumber, action, target, locator, confidence, page, "main", domEvidence, result,
                    (System.nanoTime() - started) / 1_000_000, errorCategory, exception, screenshotPath);
            throw ex;
        }
        emitStep(stepNumber, action, target, locator, confidence, page, "main", domEvidence, result,
                (System.nanoTime() - started) / 1_000_000, errorCategory, exception, screenshotPath);
    }

    private static void emitStep(int stepNumber, String action, String target, String locator, double confidence,
                                 Page page, String frameContext, String domEvidence, String result,
                                 long durationMs, String errorCategory, String exception, String screenshotPath) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("stepNumber", stepNumber);
        event.put("action", action);
        event.put("target", target);
        event.put("locator", locator);
        event.put("locatorType", locator.contains(":") ? locator.substring(0, locator.indexOf(':')) : "unknown");
        event.put("confidence", confidence);
        event.put("url", page.url());
        event.put("pageTitle", page.title());
        event.put("frameContext", frameContext);
        event.put("domEvidence", domEvidence);
        event.put("browserState", "readyState=" + page.evaluate("() => document.readyState"));
        event.put("result", result);
        event.put("durationMs", durationMs);
        if (!errorCategory.isBlank()) {
            event.put("errorCategory", errorCategory);
            event.put("exception", exception);
            event.put("rootCause", exception);
            event.put("locatorCandidates", locator);
            event.put("screenshot", screenshotPath);
        }
        System.out.println("ORANGEHRM_STEP " + event);
    }

    private static String safeDomEvidence(Page page, String target) {
        try {
            String compact = page.locator("body").innerText().replaceAll("\\s+", " ").trim();
            return compact.length() <= 240 ? compact : compact.substring(0, 240);
        } catch (RuntimeException ignored) {
            return target;
        }
    }
}
