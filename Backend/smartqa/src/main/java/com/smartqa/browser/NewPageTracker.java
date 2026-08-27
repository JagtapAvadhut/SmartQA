package com.smartqa.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generic new-tab / popup detection via page-set difference (never fixed page index).
 */
public final class NewPageTracker {

    private NewPageTracker() {
    }

    public record Capture(Set<Page> pagesBefore, int countBefore) {
    }

    public record Result(boolean opened, Page newPage, int countBefore, int countAfter) {
    }

    public static Capture capture(Page page) {
        if (page == null || page.context() == null) {
            return new Capture(Set.of(), 0);
        }
        List<Page> pages = page.context().pages();
        return new Capture(new HashSet<>(pages), pages.size());
    }

    /**
     * Arms popup listener then, after an action, resolves any newly opened page.
     */
    public static AtomicReference<Page> armPopupListener(Page page) {
        AtomicReference<Page> popup = new AtomicReference<>();
        if (page == null || page.context() == null) {
            return popup;
        }
        page.context().onPage(popup::set);
        return popup;
    }

    public static Result resolveAfterAction(Page current, Capture before, AtomicReference<Page> popupRef) {
        if (current == null || current.context() == null || before == null) {
            return new Result(false, null, 0, 0);
        }
        BrowserContext context = current.context();
        Page fromPopup = popupRef == null ? null : popupRef.get();
        List<Page> after = new ArrayList<>(context.pages());
        Page discovered = fromPopup;
        if (discovered != null && knownPage(before, discovered)) {
            discovered = null;
        }
        if (discovered == null || discovered.isClosed()) {
            for (Page candidate : after) {
                if (candidate == null || candidate.isClosed()) {
                    continue;
                }
                if (!knownPage(before, candidate) && !Objects.equals(candidate, current)) {
                    discovered = candidate;
                    break;
                }
            }
        }
        if ((discovered == null || discovered.isClosed()) && after.size() > before.countBefore()) {
            for (Page candidate : after) {
                if (candidate != null && !candidate.isClosed() && !Objects.equals(candidate, current)) {
                    discovered = candidate;
                }
            }
        }
        boolean opened = discovered != null && !discovered.isClosed();
        TraceLogger.info("BROWSER", opened ? "NEW_PAGE_DETECTED" : "NEW_PAGE_NONE",
                opened ? "New browser tab/window detected" : "No new tab/window after action",
                TraceMeta.of(
                        "countBefore", before.countBefore(),
                        "countAfter", after.size(),
                        "oldUrl", safeUrl(current),
                        "newUrl", opened ? safeUrl(discovered) : ""
                ));
        if (opened) {
            try {
                discovered.bringToFront();
            } catch (RuntimeException ignored) {
            }
        }
        return new Result(opened, opened ? discovered : null, before.countBefore(), after.size());
    }

    /**
     * Waits for a popup/new page using page-set difference. Never uses a fixed page index.
     */
    public static Result awaitNewPage(Page current, Capture before, double timeoutMs) {
        if (current == null || current.context() == null || before == null) {
            return new Result(false, null, 0, 0);
        }
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Result currentResult = resolveAfterAction(current, before, new AtomicReference<>());
            if (currentResult.opened()) {
                return currentResult;
            }
            try {
                current.waitForTimeout(100);
            } catch (RuntimeException ignored) {
                break;
            }
        }
        return resolveAfterAction(current, before, new AtomicReference<>());
    }

    /**
     * Canonical tab switch: pagesBefore → popup/new-page detection → pagesAfter → verify usable page.
     */
    public static Page switchToNewTab(Page current, Capture before, AtomicReference<Page> popupRef, double timeoutMs) {
        if (before != null && current != null && !knownPage(before, current) && !current.isClosed()) {
            verifyUsable(current);
            return current;
        }
        Result result = resolveAfterAction(current, before, popupRef);
        if (!result.opened()) {
            result = awaitNewPage(current, before, timeoutMs);
        }
        if (!result.opened() || result.newPage() == null) {
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED,
                    "No new browser tab was detected. switch_to_new_tab requires pagesBefore/pagesAfter difference.");
        }
        verifyUsable(result.newPage());
        return result.newPage();
    }

    public static void verifyUsable(Page page) {
        if (page == null || page.isClosed()) {
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED, "New tab is closed or detached");
        }
        try {
            page.bringToFront();
            String url = page.url();
            TraceLogger.info("BROWSER", "NEW_PAGE_VERIFIED", "New tab is attached, visible, and reachable",
                    TraceMeta.of("url", url == null ? "" : url));
        } catch (RuntimeException ex) {
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED, "New tab is not reachable", ex);
        }
    }

    private static boolean knownPage(Capture before, Page page) {
        if (before == null || page == null || before.pagesBefore() == null) {
            return false;
        }
        if (before.pagesBefore().contains(page)) {
            return true;
        }
        int id = System.identityHashCode(page);
        for (Page existing : before.pagesBefore()) {
            if (existing != null && System.identityHashCode(existing) == id) {
                return true;
            }
        }
        return false;
    }

    private static String safeUrl(Page page) {
        try {
            return page == null ? "" : page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
