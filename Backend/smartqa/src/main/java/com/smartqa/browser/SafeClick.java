package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.smartqa.browser.intelligence.PageStateWatcher;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;

/**
 * Clicks without blocking on Playwright's default "wait for scheduled navigations".
 * SPAs and slow form posts can leave a navigation pending forever after a successful click.
 * Also dismisses generic viewport-blocking overlays that intercept pointer events.
 */
public final class SafeClick {

    private static final int SETTLE_TIMEOUT_MS = 8_000;
    private static final int CLICK_TIMEOUT_MS = 15_000;
    private static final int MAX_OVERLAY_RETRIES = 2;

    private SafeClick() {
    }

    public static void click(Locator locator) {
        click(locator, null);
    }

    public static void click(Locator locator, Page page) {
        Locator target = preferActionableTarget(locator);
        if (page != null) {
            BlockingOverlayGuard.dismissIfBlocking(page);
        }
        RuntimeException last = null;
        for (int attempt = 0; attempt <= MAX_OVERLAY_RETRIES; attempt++) {
            try {
                if (page != null) {
                    ActionabilityVerifier.Result check = ActionabilityVerifier.verify(target, "click");
                    if (!check.ok() && check.covered()) {
                        BlockingOverlayGuard.dismissIfBlocking(page);
                    }
                }
                target.click(new Locator.ClickOptions()
                        .setNoWaitAfter(true)
                        .setTimeout(CLICK_TIMEOUT_MS));
                settle(page);
                return;
            } catch (RuntimeException ex) {
                last = ex;
                if (page == null || attempt >= MAX_OVERLAY_RETRIES
                        || !BlockingOverlayGuard.looksLikePointerIntercept(ex)) {
                    break;
                }
                TraceLogger.warn("PLAYWRIGHT", "CLICK_BLOCKED_BY_OVERLAY",
                        "Click intercepted; dismissing overlay and retrying",
                        TraceMeta.of("attempt", attempt + 1,
                                "error", truncate(ex.getMessage(), 160)));
                BlockingOverlayGuard.dismissIfBlocking(page);
                RecoveryEngine.tryRecover(page, RecoveryEngine.FailureCategory.ELEMENT_COVERED, attempt + 1);
            }
        }
        throw last;
    }

    /**
     * Prefer a semantic clickable ancestor over decorative icon/svg leaves (aria-hidden, bare &lt;i&gt;).
     * Site-agnostic — based only on live DOM roles/tags/cursor.
     */
    static Locator preferActionableTarget(Locator locator) {
        if (locator == null) {
            return null;
        }
        try {
            Object needsPromote = locator.evaluate("""
                    el => {
                      if (!el || el.nodeType !== 1) return false;
                      const tag = (el.tagName || '').toLowerCase();
                      const ariaHidden = el.getAttribute('aria-hidden') === 'true';
                      const role = (el.getAttribute('role') || '').toLowerCase();
                      if (['a', 'button', 'summary'].includes(tag)
                          || ['button', 'link', 'menuitem', 'tab'].includes(role)) {
                        return false;
                      }
                      return ariaHidden
                        || tag === 'i'
                        || tag === 'svg'
                        || tag === 'path'
                        || tag === 'p'
                        || tag === 'span'
                        || tag === 'label'
                        || (tag === 'img' && !el.getAttribute('alt'));
                    }
                    """);
            if (!(needsPromote instanceof Boolean promote) || !promote) {
                return locator;
            }
            Locator ancestor = locator.locator(
                    "xpath=ancestor-or-self::*["
                            + "self::a or self::button or self::summary"
                            + " or @role='button' or @role='link' or @role='menuitem' or @role='tab'"
                            + " or @onclick or @tabindex"
                            + "][1]");
            if (ancestor.count() > 0) {
                Locator first = ancestor.first();
                if (first.isVisible() && first.count() > 0) {
                    TraceLogger.info("PLAYWRIGHT", "CLICK_TARGET_PROMOTED",
                            "Promoted decorative leaf to actionable ancestor",
                            TraceMeta.of("reason", "decorative-or-aria-hidden"));
                    return first;
                }
            }
            // Custom cards often use cursor:pointer without roles — climb and mark once.
            Page owner = locator.page();
            if (owner == null) {
                return locator;
            }
            owner.evaluate("() => document.querySelectorAll('[data-smartqa-click-target]')"
                    + ".forEach(el => el.removeAttribute('data-smartqa-click-target'))");
            Object promoted = locator.evaluate("""
                    el => {
                      let cur = el;
                      let guard = 0;
                      while (cur && cur.nodeType === 1 && guard < 12) {
                        const tag = (cur.tagName || '').toLowerCase();
                        const role = (cur.getAttribute('role') || '').toLowerCase();
                        const style = window.getComputedStyle(cur);
                        if (['a', 'button', 'summary'].includes(tag)
                            || ['button', 'link', 'menuitem', 'tab'].includes(role)
                            || cur.getAttribute('onclick')
                            || (cur.getAttribute('tabindex') !== null && Number(cur.getAttribute('tabindex')) >= 0)
                            || style.cursor === 'pointer') {
                          cur.setAttribute('data-smartqa-click-target', '1');
                          return true;
                        }
                        cur = cur.parentElement;
                        guard += 1;
                      }
                      return false;
                    }
                    """);
            if (promoted instanceof Boolean ok && ok) {
                Locator promotedLoc = owner.locator("[data-smartqa-click-target='1']").first();
                if (promotedLoc.count() > 0 && promotedLoc.isVisible()) {
                    TraceLogger.info("PLAYWRIGHT", "CLICK_TARGET_PROMOTED",
                            "Promoted decorative leaf to pointer ancestor",
                            TraceMeta.of("reason", "cursor-pointer-ancestor"));
                    return promotedLoc;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to original locator.
        }
        return locator;
    }

    public static void settle(Page page) {
        if (page == null) {
            return;
        }
        long deadline = System.nanoTime() + SETTLE_TIMEOUT_MS * 1_000_000L;
        RuntimeException last = null;
        while (System.nanoTime() < deadline) {
            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(1_500));
                page.title();
                PageStateWatcher.waitForSubtreeSettle(page, "body", 120, 1_200);
                return;
            } catch (RuntimeException ex) {
                last = ex;
                if (!isTransientNavigation(ex)) {
                    break;
                }
            }
        }
        TraceLogger.info("PLAYWRIGHT", "CLICK_NAVIGATION_SETTLE_SKIPPED",
                "Post-click load wait elapsed; continuing",
                TraceMeta.of("url", safeUrl(page),
                        "reason", last == null ? "none" : last.getClass().getSimpleName()));
    }

    public static boolean isTransientNavigation(Throwable ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("execution context was destroyed")
                || message.contains("target closed")
                || message.contains("frame was detached")
                || message.contains("timeout");
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
