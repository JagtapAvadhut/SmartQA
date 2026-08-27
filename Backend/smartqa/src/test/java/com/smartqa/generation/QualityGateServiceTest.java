package com.smartqa.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateServiceTest {

    private final QualityGateService qualityGateService = new QualityGateService();

    @Test
    void rejectsEmptySource() {
        assertFalse(qualityGateService.validateAndCompile("").passed());
    }

    @Test
    void rejectsForbiddenApis() {
        String source = """
                import com.microsoft.playwright.Playwright;
                import org.junit.jupiter.api.Test;
                public class BadTest {
                    @Test
                    void run() {
                        Runtime.getRuntime().exec("echo hack");
                    }
                }
                """;
        assertFalse(qualityGateService.validateAndCompile(source).passed());
    }

    @Test
    void rejectsFixedWaitForTimeout() {
        String source = """
                import com.microsoft.playwright.Playwright;
                import org.junit.jupiter.api.Test;
                public class WaitTest {
                    @Test
                    void run() {
                        Playwright.create().chromium().launch().newPage().waitForTimeout(300);
                    }
                }
                """;
        assertFalse(qualityGateService.validateAndCompile(source).passed());
    }

    @Test
    void rejectsImmediateIsVisibleWithoutWaitFor() {
        String source = """
                import com.microsoft.playwright.Playwright;
                import com.microsoft.playwright.Page;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                public class VisibleTest {
                    @Test
                    void run() {
                        Page page = Playwright.create().chromium().launch().newPage();
                        Assertions.assertTrue(page.getByText("Passwords do not match").isVisible());
                    }
                }
                """;
        assertFalse(qualityGateService.validateAndCompile(source).passed());
    }

    @Test
    void compilesDeterministicPlaywrightSource() {
        String source = DeterministicPlaywrightFactory.render("ExampleDomainTest", new com.smartqa.browser.LocatorMemoryDocument(
                java.util.List.of(new com.smartqa.browser.LocatorMemoryEntry(
                        "s1",
                        "navigate",
                        "https://example.com",
                        null,
                        null,
                        1.0,
                        null,
                        null,
                        "https://example.com",
                        false,
                        null,
                        null
                ))
        ));
        QualityGateService.QualityGateResult result = qualityGateService.validateAndCompile(source);
        assertTrue(result.passed(), result.message());
    }

    @Test
    void rejectsEmptyTestMethod() {
        String source = """
                import com.microsoft.playwright.Playwright;
                import org.junit.jupiter.api.Test;
                public class EmptyTest {
                    @Test
                    void run() {
                    }
                }
                """;
        assertFalse(qualityGateService.validateAndCompile(source).passed());
    }

    @Test
    void rejectsForceClick() {
        String source = """
                import com.microsoft.playwright.Playwright;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                public class ForceTest {
                    @Test
                    void run() {
                        Playwright.create();
                        Assertions.assertTrue(true);
                        page.locator("x").click(new Locator.ClickOptions().setForce(true));
                    }
                }
                """;
        assertFalse(qualityGateService.validateAndCompile(source).passed());
    }

    @Test
    void rejectsCoordinateClick() {
        String source = """
                import com.microsoft.playwright.Playwright;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                public class CoordTest {
                    @Test
                    void run() {
                        Playwright.create();
                        Assertions.assertTrue(true);
                        page.mouse().click(12, 40);
                    }
                }
                """;
        assertFalse(qualityGateService.validateAndCompile(source).passed());
    }

    @Test
    void rejectsSeleniumAndRuntimeExec() {
        String source = """
                import com.microsoft.playwright.Playwright;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.chrome.ChromeDriver;
                public class UnsafeTest {
                    @Test
                    void run() {
                        new ChromeDriver();
                        Runtime.getRuntime().exec("whoami");
                        Assertions.assertTrue(true);
                    }
                }
                """;
        assertFalse(qualityGateService.validateAndCompile(source).passed());
    }
}
