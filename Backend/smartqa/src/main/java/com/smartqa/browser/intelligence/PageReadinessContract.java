package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.event.ProgressEvent;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Single readiness contract over {@link PageStateWatcher}. No generic Thread.sleep.
 */
public final class PageReadinessContract {

    private PageReadinessContract() {
    }

    /**
     * Bounded overlay/menu micro-settle. Replaces generic {@code page.waitForTimeout} on execution paths.
     */
    public static boolean boundedMicroSettle(Page page, int maxWaitMs) {
        return PageStateWatcher.waitForSubtreeSettle(page, "body", 80, Math.max(80, maxWaitMs));
    }

    /**
     * Cheap interactive-count probe. Never returns 0 so a blank evaluate cannot stall the default 12s wait.
     */
    public static int countInteractive(Page page) {
        if (page == null) {
            return 1;
        }
        try {
            Object raw = page.evaluate("""
                    () => document.querySelectorAll(
                      'a,button,input,select,textarea,[role=button],[role=checkbox],[role=combobox],[role=link],[role=menuitem]'
                    ).length
                    """);
            if (raw instanceof Number number) {
                return number.intValue();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return 1;
    }

    public static boolean awaitInteractive(Page page, UUID testCaseId, Consumer<ProgressEvent> progress) {
        return awaitInteractive(page, () -> countInteractive(page), testCaseId, progress);
    }

    public static boolean awaitInteractive(
            Page page,
            IntSupplier interactiveCount,
            UUID testCaseId,
            Consumer<ProgressEvent> progress
    ) {
        IntSupplier count = interactiveCount == null ? () -> 1 : interactiveCount;
        return PageStateWatcher.waitUntilInteractive(page, count, testCaseId, progress);
    }

    public static boolean awaitChange(
            Page page,
            PageStateWatcher.Observation before,
            IntSupplier interactiveCount,
            UUID testCaseId,
            Consumer<ProgressEvent> progress
    ) {
        return PageStateWatcher.waitForChange(page, before, interactiveCount, testCaseId, progress);
    }

    public static boolean awaitSubtreeSettle(Page page, String selector) {
        return PageStateWatcher.waitForSubtreeSettle(page, selector, 250, 4_000);
    }

    public static boolean awaitOverlayGone(Page page) {
        return boundedMicroSettle(page, 200);
    }

    public static boolean awaitBoundingBoxStable(Page page, Locator locator, int maxWaitMs) {
        if (page == null || locator == null) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Math.max(80, maxWaitMs);
        String previous = "";
        int stableHits = 0;
        while (System.currentTimeMillis() < deadline) {
            String box = "";
            try {
                var bbox = locator.boundingBox();
                if (bbox != null) {
                    box = bbox.x + "," + bbox.y + "," + bbox.width + "," + bbox.height;
                }
            } catch (RuntimeException ignored) {
            }
            if (!box.isBlank() && box.equals(previous)) {
                stableHits++;
                if (stableHits >= 2) {
                    return true;
                }
            } else {
                stableHits = 0;
                previous = box;
            }
            boundedMicroSettle(page, 80);
        }
        return false;
    }

    public static boolean awaitSpaUrlSettle(Page page, String urlBefore, int maxWaitMs) {
        if (page == null) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Math.max(80, maxWaitMs);
        String last = urlBefore == null ? "" : urlBefore;
        int stableHits = 0;
        while (System.currentTimeMillis() < deadline) {
            String url = "";
            try {
                url = page.url();
            } catch (RuntimeException ignored) {
            }
            if (!url.isBlank() && url.equals(last)) {
                stableHits++;
                if (stableHits >= 2) {
                    return true;
                }
            } else {
                stableHits = 0;
                last = url;
            }
            boundedMicroSettle(page, 80);
        }
        return true;
    }
}
