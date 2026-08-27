package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

/**
 * Generic navigation helpers for slow SPAs that may never reach the full {@code load} event.
 */
public final class BrowserNavigation {

    private static final int DEFAULT_TIMEOUT_MS = 90_000;
    private static final int MAX_ATTEMPTS = 3;

    private BrowserNavigation() {
    }

    public static void navigate(Page page, String url) {
        navigate(page, url, DEFAULT_TIMEOUT_MS);
    }

    public static void navigate(Page page, String url, int timeoutMs) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                navigateOnce(page, url, timeoutMs, attempt);
                if (!isErrorPage(page)) {
                    return;
                }
                lastFailure = new SmartQaException(ErrorCode.NAVIGATION_FAILURE,
                        "Navigation returned an error page: " + safeTitle(page));
                TraceLogger.warn("BROWSER", "NAVIGATION_ERROR_PAGE", "Retrying after error page",
                        TraceMeta.of("url", url, "attempt", attempt, "title", safeTitle(page)));
            } catch (RuntimeException ex) {
                lastFailure = ex;
                TraceLogger.warn("BROWSER", "NAVIGATION_RETRY", "Navigation attempt failed",
                        TraceMeta.of("url", url, "attempt", attempt, "error", ex.getMessage()));
            }
            sleepQuietly(Math.min(1000L * attempt, 4000L));
        }
        if (lastFailure instanceof SmartQaException smartQaException) {
            throw smartQaException;
        }
        throw new SmartQaException(ErrorCode.NAVIGATION_FAILURE,
                "Unable to navigate to " + url,
                lastFailure);
    }

    private static void navigateOnce(Page page, String url, int timeoutMs, int attempt) {
        TraceLogger.info("BROWSER", "NAVIGATION_STARTED", "Navigating with DOMContentLoaded wait", TraceMeta.of(
                "url", url,
                "timeoutMs", timeoutMs,
                "waitUntil", "DOMCONTENTLOADED",
                "attempt", attempt
        ));
        long started = System.nanoTime();
        Response response = page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(timeoutMs));
        int status = response == null ? 0 : response.status();
        TraceLogger.info("BROWSER", "NAVIGATION_COMPLETED", "Initial navigation committed",
                (System.nanoTime() - started) / 1_000_000,
                TraceMeta.of("url", url, "finalUrl", page.url(), "title", safeTitle(page), "status", status));
        if (status >= 500) {
            throw new SmartQaException(ErrorCode.NAVIGATION_FAILURE, "Navigation HTTP status " + status + " for " + url);
        }
        waitForApplicationShell(page, timeoutMs);
    }

    static void waitForApplicationShell(Page page, int timeoutMs) {
        long deadline = System.nanoTime() + (long) timeoutMs * 1_000_000L;
        int pollMs = 250;
        while (System.nanoTime() < deadline) {
            if (isErrorPage(page)) {
                return;
            }
            try {
                Object count = page.evaluate("""
                        () => document.querySelectorAll(
                          'input,button,a,select,textarea,[role],[data-testid]'
                        ).length
                        """);
                if (count instanceof Number number && number.intValue() > 0) {
                    TraceLogger.info("BROWSER", "APPLICATION_SHELL_READY", "Interactive shell detected", TraceMeta.of(
                            "interactiveCount", number.intValue(),
                            "url", page.url(),
                            "title", safeTitle(page)
                    ));
                    return;
                }
            } catch (RuntimeException ignored) {
            }
            try {
                page.waitForFunction(
                        "() => document.readyState === 'interactive' || document.readyState === 'complete'",
                        new Page.WaitForFunctionOptions().setTimeout(pollMs));
            } catch (RuntimeException ignored) {
            }
            pollMs = Math.min(pollMs * 2, 2000);
        }
        TraceLogger.warn("BROWSER", "APPLICATION_SHELL_TIMEOUT", "Timed out waiting for application shell", TraceMeta.of(
                "url", page.url(),
                "title", safeTitle(page)
        ));
    }

    static boolean isErrorPage(Page page) {
        String title = safeTitle(page).toLowerCase();
        if (title.contains("500 internal server error")
                || title.contains("502 bad gateway")
                || title.contains("503 service unavailable")
                || title.contains("504 gateway timeout")
                || title.contains("401 unauthorized")) {
            return true;
        }
        try {
            String body = page.locator("body").innerText().toLowerCase();
            return body.contains("internal server error") && body.contains("nginx");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String safeTitle(Page page) {
        try {
            return page.title() == null ? "" : page.title();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static void sleepQuietly(long millis) {
        // Prefer parking over Thread.sleep so interrupt handling stays consistent on wait loops.
        java.util.concurrent.locks.LockSupport.parkNanos(Math.max(0, millis) * 1_000_000L);
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
        }
    }
}
