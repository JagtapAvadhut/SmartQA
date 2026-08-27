package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.pipeline.FailureEvidenceCollector;

import java.net.URI;
import java.util.Locale;

/**
 * Generic host-context guard for search/navigation recovery.
 * No site-specific exceptions — only compares requested application domain vs current page.
 */
public final class HostContextGuard {

    private HostContextGuard() {
    }

    public static boolean hostDiverged(String expectedApplicationUrl, String currentUrl) {
        String expected = FailureEvidenceCollector.hostOf(expectedApplicationUrl);
        String actual = FailureEvidenceCollector.hostOf(currentUrl);
        if (expected.isBlank() || actual.isBlank()) {
            return false;
        }
        if (expected.equalsIgnoreCase(actual)) {
            return false;
        }
        if (!FailureEvidenceCollector.sameRegistrableDomain(expected, actual)) {
            return false;
        }
        // Listing/search/city sibling subdomains are valid application state.
        // Only export-like / external-trade hosts are a harmful divergence worth restoring.
        return isExportLikeHost(currentUrl) || looksLikeExternalTradeRedirect(currentUrl);
    }

    /**
     * If current page is on an export-like sibling host of the same registrable domain,
     * navigate back to the expected application base URL. Do not yank listing/search subdomains
     * back to the marketing homepage — that destroys verified search/location state.
     */
    public static boolean restoreExpectedHostIfNeeded(Page page, String expectedApplicationUrl) {
        if (page == null || expectedApplicationUrl == null || expectedApplicationUrl.isBlank()) {
            return false;
        }
        String current = safeUrl(page);
        if (!hostDiverged(expectedApplicationUrl, current)) {
            return false;
        }
        String target = baseUrl(expectedApplicationUrl);
        TraceLogger.warn("SEARCH", "WRONG_HOST_DETECTED", "Restoring expected application host", TraceMeta.of(
                "from", FailureEvidenceCollector.hostOf(current),
                "to", FailureEvidenceCollector.hostOf(target)
        ));
        try {
            page.navigate(target);
            page.waitForLoadState();
            SafeClick.settle(page);
            boolean restored = !hostDiverged(expectedApplicationUrl, safeUrl(page));
            TraceLogger.info("SEARCH", "HOST_RESTORED", "Expected host restore finished", TraceMeta.of(
                    "restored", restored,
                    "url", safeUrl(page)
            ));
            return restored;
        } catch (RuntimeException ex) {
            TraceLogger.warn("SEARCH", "HOST_RESTORE_FAILED", "Could not restore expected host", TraceMeta.of(
                    "message", ex.getMessage() == null ? "" : ex.getMessage()
            ));
            return false;
        }
    }

    /**
     * True when the browser left the application's registrable domain entirely
     * (demo host → unrelated marketing host). Used for classification, not automatic homepage restore.
     */
    public static boolean leftApplication(String expectedApplicationUrl, String currentUrl) {
        String expected = FailureEvidenceCollector.hostOf(expectedApplicationUrl);
        String actual = FailureEvidenceCollector.hostOf(currentUrl);
        if (expected.isBlank() || actual.isBlank() || expected.equalsIgnoreCase(actual)) {
            return false;
        }
        return !FailureEvidenceCollector.sameRegistrableDomain(expected, actual);
    }

    /**
     * If the active page left the application registrable domain, prefer a sibling tab that is
     * still on-host, otherwise {@code goBack()} once. Returns the page that should be used next.
     * Never encodes a website-specific URL.
     */
    public static Page recoverIfLeftApplication(Page page, String expectedApplicationUrl) {
        if (page == null || !leftApplication(expectedApplicationUrl, safeUrl(page))) {
            return page;
        }
        String from = safeUrl(page);
        TraceLogger.warn("BROWSER", "WRONG_PAGE_DETECTED", "Active page left application host", TraceMeta.of(
                "expectedHost", FailureEvidenceCollector.hostOf(expectedApplicationUrl),
                "actualUrl", from
        ));
        Page sibling = findOnHostSibling(page, expectedApplicationUrl);
        if (sibling != null) {
            try {
                sibling.bringToFront();
            } catch (RuntimeException ignored) {
            }
            TraceLogger.info("BROWSER", "HOST_RESTORED", "Switched to on-host sibling tab", TraceMeta.of(
                    "from", from,
                    "to", safeUrl(sibling)
            ));
            return sibling;
        }
        try {
            page.goBack();
            page.waitForLoadState();
            SafeClick.settle(page);
        } catch (RuntimeException ex) {
            TraceLogger.warn("BROWSER", "HOST_RESTORE_FAILED", "goBack after host leave failed", TraceMeta.of(
                    "message", ex.getMessage() == null ? "" : ex.getMessage()
            ));
        }
        TraceLogger.info("BROWSER", "HOST_RESTORE_ATTEMPTED", "goBack after leaving application host", TraceMeta.of(
                "from", from,
                "to", safeUrl(page),
                "restored", !leftApplication(expectedApplicationUrl, safeUrl(page))
        ));
        return page;
    }

    public static void assertStillInApplication(Page page, String expectedApplicationUrl) {
        if (page == null || !leftApplication(expectedApplicationUrl, safeUrl(page))) {
            return;
        }
        throw new SmartQaException(ErrorCode.WRONG_PAGE_STATE,
                "WRONG_PAGE_STATE: browser left the application host. expected="
                        + FailureEvidenceCollector.hostOf(expectedApplicationUrl)
                        + " actual=" + safeUrl(page));
    }

    /**
     * A content click that lands on a login/auth URL is WRONG_PAGE_STATE, not a valid next step.
     * One {@code goBack()} is attempted. Login-intent clicks are left alone.
     */
    public static Page recoverIfUnexpectedAuthPage(Page page, String clickIntentPrefix) {
        if (page == null || "LOGIN".equalsIgnoreCase(clickIntentPrefix == null ? "" : clickIntentPrefix)) {
            return page;
        }
        if (!AssertionTruthEngine.looksLikeLoginUrl(safeUrl(page))) {
            return page;
        }
        String from = safeUrl(page);
        TraceLogger.warn("BROWSER", "UNEXPECTED_AUTH_PAGE", "Content click landed on a login/auth URL", TraceMeta.of(
                "url", from,
                "clickIntent", clickIntentPrefix == null ? "" : clickIntentPrefix
        ));
        try {
            page.goBack();
            page.waitForLoadState();
            SafeClick.settle(page);
        } catch (RuntimeException ex) {
            TraceLogger.warn("BROWSER", "AUTH_PAGE_RESTORE_FAILED", "goBack from unexpected auth URL failed", TraceMeta.of(
                    "message", ex.getMessage() == null ? "" : ex.getMessage()
            ));
        }
        TraceLogger.info("BROWSER", "AUTH_PAGE_RESTORE_ATTEMPTED", "goBack after unexpected auth URL", TraceMeta.of(
                "from", from,
                "to", safeUrl(page),
                "stillAuth", AssertionTruthEngine.looksLikeLoginUrl(safeUrl(page))
        ));
        return page;
    }

    public static void assertNotUnexpectedAuthPage(Page page, String clickIntentPrefix) {
        if (page == null || "LOGIN".equalsIgnoreCase(clickIntentPrefix == null ? "" : clickIntentPrefix)) {
            return;
        }
        if (!AssertionTruthEngine.looksLikeLoginUrl(safeUrl(page))) {
            return;
        }
        throw new SmartQaException(ErrorCode.WRONG_PAGE_STATE,
                "WRONG_PAGE_STATE: content click landed on a login/auth page. url=" + safeUrl(page));
    }

    public static boolean isOffHostPage(Page candidate, String expectedApplicationUrl) {
        return candidate != null && leftApplication(expectedApplicationUrl, safeUrl(candidate));
    }

    private static Page findOnHostSibling(Page page, String expectedApplicationUrl) {
        try {
            for (Page sibling : page.context().pages()) {
                if (sibling == null || sibling.isClosed() || sibling.equals(page)) {
                    continue;
                }
                if (!leftApplication(expectedApplicationUrl, safeUrl(sibling))) {
                    return sibling;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    public static boolean isExportLikeHost(String url) {
        String host = FailureEvidenceCollector.hostOf(url);
        return host.startsWith("export.") || host.contains(".export.");
    }

    /**
     * Prefer suggestion candidates that do not navigate to export-like hosts when domestic flow is expected.
     */
    public static boolean looksLikeExternalTradeRedirect(String hrefOrText) {
        if (hrefOrText == null || hrefOrText.isBlank()) {
            return false;
        }
        String lower = hrefOrText.toLowerCase(Locale.ROOT);
        // Word-boundary match: "/exporters" and "exporting" must not trip a host restore.
        return lower.contains("export.")
                || lower.matches(".*(?:^|/)export(?:[/?#.]|$).*")
                || lower.contains("international buyer")
                || lower.contains("export buyer")
                || lower.contains("export buyers");
    }

    private static String baseUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null) {
                return url;
            }
            int port = uri.getPort();
            return port > 0 ? scheme + "://" + host + ":" + port + "/" : scheme + "://" + host + "/";
        } catch (Exception ex) {
            return url;
        }
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
