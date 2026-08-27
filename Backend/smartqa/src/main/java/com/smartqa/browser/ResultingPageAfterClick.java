package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.concurrent.atomic.AtomicReference;

/**
 * After a click, adopts the page that actually resulted: same tab, modal, or new tab.
 * Does not click. Callers click, then resolve.
 */
public final class ResultingPageAfterClick {

    private ResultingPageAfterClick() {
    }

    public record Armed(NewPageTracker.Capture before, AtomicReference<Page> popup) {
    }

    public static Armed arm(Page page) {
        return new Armed(NewPageTracker.capture(page), NewPageTracker.armPopupListener(page));
    }

    public static Page resolve(Page current, Armed armed, String expectedApplicationUrl) {
        if (current == null || armed == null) {
            return current;
        }
        NewPageTracker.Result result = NewPageTracker.resolveAfterAction(current, armed.before(), armed.popup());
        if ((result == null || !result.opened()) && current.context() != null) {
            result = NewPageTracker.awaitNewPage(current, armed.before(), 5_000);
        }
        if (result != null && result.opened() && result.newPage() != null) {
            if (!HostContextGuard.isOffHostPage(result.newPage(), expectedApplicationUrl)) {
                TraceLogger.info("BROWSER", "RESULTING_PAGE_NEW_TAB", "Adopted on-host tab after click", TraceMeta.of(
                        "oldUrl", safeUrl(current),
                        "newUrl", safeUrl(result.newPage()),
                        "tabCount", result.countAfter()
                ));
                SafeClick.settle(result.newPage());
                return result.newPage();
            }
            TraceLogger.warn("BROWSER", "RESULTING_PAGE_OFF_HOST_IGNORED", "Kept current page; new tab left the app host",
                    TraceMeta.of("newUrl", safeUrl(result.newPage()), "keptUrl", safeUrl(current)));
        }
        SafeClick.settle(current);
        return current;
    }

    private static String safeUrl(Page page) {
        if (page == null) {
            return "";
        }
        try {
            return page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
