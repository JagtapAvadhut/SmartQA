package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostContextGuardTest {

    @Test
    void detectsExportSubdomainDivergenceOnSameRegistrableDomain() {
        assertTrue(HostContextGuard.hostDiverged(
                "https://www.example.com/",
                "https://export.example.com/"));
    }

    @Test
    void listingOrSearchSiblingSubdomainIsNotHarmfulDivergence() {
        assertFalse(HostContextGuard.hostDiverged(
                "https://www.example.com/",
                "https://search.example.com/q=phones"));
        assertFalse(HostContextGuard.hostDiverged(
                "https://www.example.com/",
                "https://dir.example.com/search"));
        assertFalse(HostContextGuard.hostDiverged(
                "https://www.example.com/",
                "https://m.example.com/"));
    }

    @Test
    void doesNotFlagSameHost() {
        assertFalse(HostContextGuard.hostDiverged(
                "https://www.example.com/search",
                "https://www.example.com/results"));
    }

    @Test
    void doesNotFlagUnrelatedDomains() {
        assertFalse(HostContextGuard.hostDiverged(
                "https://www.indiamart.com/",
                "https://www.urbancompany.com/"));
    }

    @Test
    void detectsExportLikeRedirectText() {
        assertTrue(HostContextGuard.looksLikeExternalTradeRedirect("Export buyers on export.indiamart.com"));
        assertFalse(HostContextGuard.looksLikeExternalTradeRedirect("Double screen mobile phone near Mumbai"));
        assertFalse(HostContextGuard.looksLikeExternalTradeRedirect("https://dir.example.com/impcat/mobile-phone-exporters.html"));
    }

    @Test
    void leftApplicationWhenRegistrableDomainChanges() {
        assertTrue(HostContextGuard.leftApplication(
                "https://opensource-demo.orangehrmlive.com/web/index.php/auth/login",
                "https://www.orangehrm.com/"));
        assertFalse(HostContextGuard.leftApplication(
                "https://www.example.com/search",
                "https://search.example.com/q=phones"));
    }

    @Test
    void leftApplicationWhenClickLandsOnUnrelatedHost() {
        assertTrue(HostContextGuard.leftApplication(
                "https://www.example.com/",
                "https://www.other-site.test/login"));
    }

    @Test
    void recoverIfLeftApplicationUsesGoBackToOnHostHistory() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate("https://example.com");
            page.navigate("https://example.org");
            assertTrue(HostContextGuard.leftApplication("https://example.com", page.url()));
            Page restored = HostContextGuard.recoverIfLeftApplication(page, "https://example.com");
            assertFalse(HostContextGuard.leftApplication("https://example.com", restored.url()));
            HostContextGuard.assertStillInApplication(restored, "https://example.com");
            browser.close();
        }
    }

    @Test
    void recoverIfUnexpectedAuthPageGoesBackFromLoginUrl() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate("https://example.com");
            page.navigate("https://example.com/login");
            assertTrue(AssertionTruthEngine.looksLikeLoginUrl(page.url()));
            Page restored = HostContextGuard.recoverIfUnexpectedAuthPage(page, "CLICK");
            assertFalse(AssertionTruthEngine.looksLikeLoginUrl(restored.url()));
            HostContextGuard.assertNotUnexpectedAuthPage(restored, "CLICK");
            browser.close();
        }
    }
}
