package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.browser.intelligence.PageReadinessContract;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

/**
 * Bounded incremental scroll until a semantic target appears or end-of-page is reached.
 * Never uses fixed scroll counts as the only strategy.
 */
public final class ScrollIntelligence {

    private static final int MAX_ATTEMPTS = 12;

    private ScrollIntelligence() {
    }

    public static boolean scrollToTarget(Page page, String target) {
        if (page == null) {
            return false;
        }
        String hint = target == null ? "" : target.trim();
        if (hint.isBlank() || isGenericScroll(hint)) {
            return scrollDown(page, 3);
        }
        if (tryBringIntoView(page, hint)) {
            return true;
        }
        double lastY = -1;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            Object yObj = page.evaluate("() => window.scrollY");
            double y = yObj instanceof Number n ? n.doubleValue() : 0;
            page.evaluate("() => window.scrollBy(0, Math.max(320, Math.floor(window.innerHeight * 0.75)))");
            PageReadinessContract.boundedMicroSettle(page, 250);
            if (tryBringIntoView(page, hint)) {
                TraceLogger.info("BROWSER", "SCROLL_TARGET_FOUND", "Scrolled until target appeared", TraceMeta.of(
                        "target", hint, "attempts", i + 1
                ));
                return true;
            }
            Object newYObj = page.evaluate("() => window.scrollY");
            double newY = newYObj instanceof Number n ? n.doubleValue() : 0;
            if (Math.abs(newY - y) < 2 || Math.abs(newY - lastY) < 2) {
                TraceLogger.info("BROWSER", "SCROLL_END_OF_PAGE", "Reached end of page without target", TraceMeta.of(
                        "target", hint, "attempts", i + 1
                ));
                return false;
            }
            lastY = newY;
        }
        return false;
    }

    private static boolean isGenericScroll(String hint) {
        String lower = hint.toLowerCase();
        return lower.equals("down") || lower.equals("up") || lower.equals("scroll")
                || lower.equals("scroll down") || lower.equals("scroll up");
    }

    private static boolean scrollDown(Page page, int steps) {
        for (int i = 0; i < steps; i++) {
            page.evaluate("() => window.scrollBy(0, Math.max(320, Math.floor(window.innerHeight * 0.75)))");
            PageReadinessContract.boundedMicroSettle(page, 200);
        }
        return true;
    }

    private static boolean tryBringIntoView(Page page, String hint) {
        try {
            Locator byRole = page.getByText(hint, new Page.GetByTextOptions().setExact(false));
            int count = Math.min(byRole.count(), 8);
            for (int i = 0; i < count; i++) {
                Locator candidate = byRole.nth(i);
                if (candidate.isVisible()) {
                    candidate.scrollIntoViewIfNeeded();
                    return true;
                }
            }
            Locator any = page.locator("text=" + hint).first();
            if (any.count() > 0) {
                any.scrollIntoViewIfNeeded();
                return any.isVisible();
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }
}
