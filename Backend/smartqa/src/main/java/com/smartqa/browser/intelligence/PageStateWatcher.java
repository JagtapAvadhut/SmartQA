package com.smartqa.browser.intelligence;



import com.microsoft.playwright.Page;

import com.microsoft.playwright.options.LoadState;

import com.smartqa.debug.TraceLogger;

import com.smartqa.debug.TraceMeta;

import com.smartqa.event.ProgressEvent;



import java.util.Map;

import java.util.UUID;

import java.util.function.Consumer;

import java.util.function.IntSupplier;



public final class PageStateWatcher {



    private static final int DEFAULT_MAX_WAIT_MS = 12_000;

    private static final int INITIAL_POLL_MS = 200;

    private static final int MAX_POLL_MS = 2_000;



    public record Observation(String url, String title, int interactiveCount) {

    }



    private PageStateWatcher() {

    }



    public static Observation capture(Page page, int interactiveCount) {
        String url = "";
        String title = "";
        try {
            url = page.url();
        } catch (RuntimeException ignored) {
        }
        try {
            title = page.title();
        } catch (RuntimeException ignored) {
        }
        return new Observation(url, title, interactiveCount);
    }



    public static boolean waitForChange(

            Page page,

            Observation before,

            IntSupplier interactiveCount,

            UUID testCaseId,

            Consumer<ProgressEvent> progress) {

        return waitForChange(page, before, interactiveCount, testCaseId, progress, DEFAULT_MAX_WAIT_MS);

    }



    public static boolean waitForChange(

            Page page,

            Observation before,

            IntSupplier interactiveCount,

            UUID testCaseId,

            Consumer<ProgressEvent> progress,

            int maxWaitMs) {

        long started = System.nanoTime();

        TraceLogger.info("BROWSER", "STATE_WAIT_STARTED", "Waiting for dynamic UI", TraceMeta.of(

                "reason", "Waiting for page state change",

                "expectedUrl", before.url(),

                "expectedTitle", before.title()

        ));

        waitForUrlOrTitleChange(page, before);

        waitForPageSettle(page);

        long deadline = System.nanoTime() + (long) maxWaitMs * 1_000_000L;

        int pollMs = INITIAL_POLL_MS;

        while (System.nanoTime() < deadline) {

            Observation after = capture(page, interactiveCount.getAsInt());

            boolean urlChanged = !after.url().equals(before.url());

            boolean titleChanged = !after.title().equals(before.title());

            boolean hydrated = after.interactiveCount() > 0;

            boolean domChanged = after.interactiveCount() != before.interactiveCount();

            if ((urlChanged || titleChanged || domChanged) && hydrated) {

                emitStateChanged(progress, testCaseId, after, started, urlChanged, titleChanged, domChanged);

                return true;

            }

            pollMs = boundedPoll(page, pollMs, deadline);

        }

        Observation observed = capture(page, interactiveCount.getAsInt());

        TraceLogger.warn("BROWSER", "STATE_WAIT_TIMEOUT", "Timed out waiting for page state change", TraceMeta.of(

                "durationMs", (System.nanoTime() - started) / 1_000_000,

                "expectedUrl", before.url(),

                "observedUrl", observed.url(),

                "expectedTitle", before.title(),

                "observedTitle", observed.title(),

                "interactiveCount", observed.interactiveCount()

        ));

        return false;

    }



    public static boolean waitUntilInteractive(

            Page page,

            IntSupplier interactiveCount,

            UUID testCaseId,

            Consumer<ProgressEvent> progress) {

        return waitUntilInteractive(page, interactiveCount, testCaseId, progress, DEFAULT_MAX_WAIT_MS);

    }



    public static boolean waitUntilInteractive(

            Page page,

            IntSupplier interactiveCount,

            UUID testCaseId,

            Consumer<ProgressEvent> progress,

            int maxWaitMs) {

        long started = System.nanoTime();

        waitForPageSettle(page);

        long deadline = System.nanoTime() + (long) maxWaitMs * 1_000_000L;

        int pollMs = INITIAL_POLL_MS;

        while (System.nanoTime() < deadline) {

            int count = interactiveCount.getAsInt();

            if (count > 0) {

                TraceLogger.info("BROWSER", "STATE_CHANGED", "Interactive controls became available",

                        (System.nanoTime() - started) / 1_000_000,

                        TraceMeta.of("interactiveCount", count, "url", page.url()));

                if (progress != null) {

                    progress.accept(ProgressEvent.generation(

                            "STATE_CHANGED",

                            "Interactive controls became available",

                            testCaseId,

                            Map.of("url", page.url(), "interactiveCount", count)

                    ));

                }

                return true;

            }

            pollMs = boundedPoll(page, pollMs, deadline);

        }

        TraceLogger.warn("BROWSER", "STATE_WAIT_TIMEOUT", "Timed out waiting for interactive controls", TraceMeta.of(

                "durationMs", (System.nanoTime() - started) / 1_000_000,

                "url", page.url(),

                "interactiveCount", interactiveCount.getAsInt()

        ));

        return false;

    }



    private static void emitStateChanged(

            Consumer<ProgressEvent> progress,

            UUID testCaseId,

            Observation after,

            long started,

            boolean urlChanged,

            boolean titleChanged,

            boolean domChanged) {

        if (progress != null) {

            progress.accept(ProgressEvent.generation(

                    "STATE_CHANGED",

                    "Page state changed",

                    testCaseId,

                    Map.of("url", after.url(), "title", after.title())

            ));

        }

        TraceLogger.info("BROWSER", "STATE_CHANGED", "Page state changed",

                (System.nanoTime() - started) / 1_000_000,

                TraceMeta.of(

                        "urlChanged", urlChanged,

                        "titleChanged", titleChanged,

                        "domChanged", domChanged,

                        "interactiveCount", after.interactiveCount(),

                        "url", after.url(),

                        "title", after.title()

                ));

    }



    private static int boundedPoll(Page page, int pollMs, long deadline) {

        long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;

        if (remainingMs <= 0) {

            return pollMs;

        }

        int waitMs = (int) Math.min(pollMs, remainingMs);

        try {

            page.waitForFunction(

                    "() => document.readyState === 'interactive' || document.readyState === 'complete'",

                    new Page.WaitForFunctionOptions().setTimeout(waitMs));

        } catch (RuntimeException ignored) {

        }

        return Math.min(pollMs * 2, MAX_POLL_MS);

    }



    private static void waitForPageSettle(Page page) {
        try {
            Object ready = page.evaluate("() => document.readyState");
            if ("complete".equalsIgnoreCase(String.valueOf(ready))) {
                return;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(4000));
        } catch (RuntimeException ignored) {
        }
        try {
            page.waitForFunction("() => document.readyState === 'interactive' || document.readyState === 'complete'",
                    new Page.WaitForFunctionOptions().setTimeout(1500));
        } catch (RuntimeException ignored) {
        }
    }



    private static void waitForUrlOrTitleChange(Page page, Observation before) {

        try {

            page.waitForFunction(

                    "(expected) => window.location.href !== expected.url || document.title !== expected.title",

                    Map.of("url", before.url(), "title", before.title()),

                    new Page.WaitForFunctionOptions().setTimeout(700));

        } catch (RuntimeException ignored) {

        }

    }

    /**
     * Wait until mutations in a relevant subtree go quiet. Not global document idle.
     */
    public static boolean waitForSubtreeSettle(Page page, String selector, int quietMs, int maxWaitMs) {
        if (page == null) {
            return true;
        }
        String sel = selector == null || selector.isBlank() ? "body" : selector;
        int quiet = Math.max(50, quietMs);
        int max = Math.max(quiet, maxWaitMs);
        try {
            page.evaluate("""
                    ({ selector, quietMs, maxWaitMs }) => new Promise((resolve) => {
                      const root = document.querySelector(selector) || document.body;
                      if (!root) { resolve(true); return; }
                      let timer = null;
                      const finish = (ok) => { try { observer.disconnect(); } catch (e) {} resolve(ok); };
                      const observer = new MutationObserver(() => {
                        if (timer) clearTimeout(timer);
                        timer = setTimeout(() => finish(true), quietMs);
                      });
                      observer.observe(root, { childList: true, subtree: true, attributes: true, characterData: true });
                      timer = setTimeout(() => finish(true), quietMs);
                      setTimeout(() => finish(true), maxWaitMs);
                    })
                    """, Map.of("selector", sel, "quietMs", quiet, "maxWaitMs", max));
            waitUntilBusyGone(page, Math.min(1_500, max));
            return true;
        } catch (RuntimeException ex) {
            TraceLogger.warn("BROWSER", "SUBTREE_SETTLE_SKIPPED", "Subtree settle skipped", TraceMeta.of(
                    "selector", sel,
                    "reason", ex.getMessage() == null ? "" : ex.getMessage()
            ));
            return false;
        }
    }

    static void waitUntilBusyGone(Page page, int maxWaitMs) {
        if (page == null) {
            return;
        }
        try {
            page.waitForFunction(
                    "() => !document.querySelector('[aria-busy=true], [role=progressbar], [aria-live=assertive]')",
                    new Page.WaitForFunctionOptions().setTimeout(Math.max(200, maxWaitMs)));
        } catch (RuntimeException ignored) {
        }
    }

}


