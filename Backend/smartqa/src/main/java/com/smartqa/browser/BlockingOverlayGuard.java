package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.smartqa.browser.intelligence.PageReadinessContract;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;
import java.util.Map;

/**
 * Site-agnostic recovery when a full-viewport overlay intercepts pointer events.
 * Never keyed to a URL or product — only live DOM geometry and accessibility.
 */
public final class BlockingOverlayGuard {

    private static final double MIN_VIEWPORT_COVERAGE = 0.35;
    private static final int DISMISS_TIMEOUT_MS = 1_500;

    private BlockingOverlayGuard() {
    }

    /**
     * @return true if an overlay was detected and at least one dismiss attempt ran
     */
    public static boolean dismissIfBlocking(Page page) {
        if (page == null) {
            return false;
        }
        boolean consent = dismissConsentBanners(page);
        Map<String, Object> blocker = findTopBlocker(page);
        if (blocker == null || blocker.isEmpty()) {
            return consent;
        }
        TraceLogger.info("PLAYWRIGHT", "OVERLAY_DETECTED", "Blocking overlay detected before click",
                TraceMeta.of(
                        "coverage", String.valueOf(blocker.get("coverage")),
                        "zIndex", String.valueOf(blocker.get("zIndex")),
                        "tag", String.valueOf(blocker.get("tag")),
                        "role", String.valueOf(blocker.get("role")),
                        "className", truncate(String.valueOf(blocker.get("className")), 80)
                ));
        boolean dismissed = tryEscape(page)
                || tryCloseControl(page)
                || dismissConsentBanners(page)
                || tryBackdropClick(page, blocker);
        if (dismissed) {
            PageReadinessContract.boundedMicroSettle(page, 150);
            TraceLogger.info("PLAYWRIGHT", "OVERLAY_DISMISSED", "Blocking overlay dismiss attempted",
                    TraceMeta.of("stillBlocking", String.valueOf(findTopBlocker(page) != null)));
        }
        return true;
    }

    /**
     * When a target is covered, identify the covering node via elementFromPoint and try
     * accessible dismiss/accept controls on that subtree. Site-agnostic.
     */
    public static boolean dismissCoveringElement(Page page, Locator target) {
        if (page == null || target == null) {
            return false;
        }
        try {
            Object raw = target.evaluate("""
                    el => {
                      if (!el || !el.getBoundingClientRect) return null;
                      const r = el.getBoundingClientRect();
                      const x = Math.min(Math.max(r.left + r.width / 2, 0), (window.innerWidth || 1) - 1);
                      const y = Math.min(Math.max(r.top + r.height / 2, 0), (window.innerHeight || 1) - 1);
                      const top = document.elementFromPoint(x, y);
                      if (!top || el === top || el.contains(top) || top.contains(el)) return null;
                      let cur = top;
                      let guard = 0;
                      while (cur && cur.nodeType === 1 && guard < 8) {
                        const role = (cur.getAttribute('role') || '').toLowerCase();
                        const cls = (cur.className && typeof cur.className === 'string')
                          ? cur.className.toLowerCase() : '';
                        const id = (cur.id || '').toLowerCase();
                        if (role === 'dialog' || role === 'alertdialog'
                            || cur.getAttribute('aria-modal') === 'true'
                            || cls.match(/(modal|dialog|overlay|backdrop|popup|cookie|consent|banner|drawer)/)
                            || id.match(/(modal|dialog|overlay|cookie|consent|banner)/)) {
                          cur.setAttribute('data-smartqa-cover', '1');
                          return {
                            tag: (cur.tagName || '').toLowerCase(),
                            role,
                            className: cls.slice(0, 120),
                            text: (cur.innerText || '').slice(0, 80)
                          };
                        }
                        cur = cur.parentElement;
                        guard += 1;
                      }
                      top.setAttribute('data-smartqa-cover', '1');
                      return {
                        tag: (top.tagName || '').toLowerCase(),
                        role: top.getAttribute('role') || '',
                        className: ((top.className && typeof top.className === 'string')
                          ? top.className : '').slice(0, 120),
                        text: (top.innerText || '').slice(0, 80)
                      };
                    }
                    """);
            if (!(raw instanceof Map<?, ?> info) || info.isEmpty()) {
                return dismissConsentBanners(page);
            }
            TraceLogger.info("PLAYWRIGHT", "COVERING_ELEMENT", "Target covered by element",
                    TraceMeta.of(
                            "tag", String.valueOf(info.get("tag")),
                            "role", String.valueOf(info.get("role")),
                            "className", truncate(String.valueOf(info.get("className")), 80),
                            "text", truncate(String.valueOf(info.get("text")), 80)
                    ));
            Locator cover = page.locator("[data-smartqa-cover='1']").first();
            boolean dismissed = tryCloseWithin(cover) || dismissConsentBanners(page) || tryEscape(page);
            page.evaluate("() => document.querySelectorAll('[data-smartqa-cover]')"
                    + ".forEach(el => el.removeAttribute('data-smartqa-cover'))");
            return dismissed;
        } catch (RuntimeException ex) {
            return dismissConsentBanners(page);
        }
    }

    /**
     * Generic consent / cookie / notification dismiss via accessible names.
     */
    public static boolean dismissConsentBanners(Page page) {
        if (page == null) {
            return false;
        }
        String[] names = {
                "Accept", "Accept all", "Accept All", "Accept cookies", "Accept Cookies",
                "Agree", "I agree", "I Agree", "Allow", "Allow all", "Allow All",
                "Got it", "Got It", "OK", "Okay", "Continue", "Yes", "Allow cookies"
        };
        for (String name : names) {
            try {
                Locator byRole = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
                if (byRole.count() > 0 && byRole.first().isVisible()) {
                    byRole.first().click(new Locator.ClickOptions()
                            .setTimeout(DISMISS_TIMEOUT_MS)
                            .setNoWaitAfter(true));
                    PageReadinessContract.boundedMicroSettle(page, 150);
                    TraceLogger.info("PLAYWRIGHT", "CONSENT_DISMISSED", "Dismissed consent control",
                            TraceMeta.of("name", name));
                    return true;
                }
            } catch (RuntimeException ignored) {
                // try next
            }
        }
        return false;
    }

    private static boolean tryCloseWithin(Locator root) {
        if (root == null) {
            return false;
        }
        String[] names = {"Close", "Dismiss", "Cancel", "Accept", "Agree", "OK", "Got it", "×", "✕"};
        for (String name : names) {
            try {
                Locator btn = root.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name));
                if (btn.count() > 0 && btn.first().isVisible()) {
                    btn.first().click(new Locator.ClickOptions()
                            .setTimeout(DISMISS_TIMEOUT_MS)
                            .setNoWaitAfter(true));
                    return true;
                }
            } catch (RuntimeException ignored) {
                // next
            }
        }
        try {
            Locator close = root.locator(
                    "[aria-label*='close' i], [aria-label*='dismiss' i], button.close, [class*='close' i]");
            if (close.count() > 0 && close.first().isVisible()) {
                close.first().click(new Locator.ClickOptions()
                        .setTimeout(DISMISS_TIMEOUT_MS)
                        .setNoWaitAfter(true));
                return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    public static boolean looksLikePointerIntercept(Throwable ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("intercepts pointer events");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findTopBlocker(Page page) {
        try {
            Object raw = page.evaluate("""
                    () => {
                      const vw = window.innerWidth || 1;
                      const vh = window.innerHeight || 1;
                      const area = vw * vh;
                      let best = null;
                      let bestCoverage = 0;
                      const nodes = document.querySelectorAll('body *');
                      for (const el of nodes) {
                        const style = window.getComputedStyle(el);
                        if (style.pointerEvents === 'none' || style.display === 'none'
                            || style.visibility === 'hidden' || Number(style.opacity) === 0) {
                          continue;
                        }
                        const pos = style.position;
                        if (pos !== 'fixed' && pos !== 'sticky' && pos !== 'absolute') {
                          continue;
                        }
                        const r = el.getBoundingClientRect();
                        if (r.width < 40 || r.height < 40) {
                          continue;
                        }
                        const overlapW = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0));
                        const overlapH = Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
                        const coverage = (overlapW * overlapH) / area;
                        if (coverage < %f) {
                          continue;
                        }
                        // Prefer true viewport dimmers / modal backdrops over large absolute sections.
                        const z = Number.parseInt(style.zIndex, 10);
                        const zScore = Number.isFinite(z) ? z : 0;
                        const looksModal = !!(el.getAttribute('role') === 'dialog'
                          || el.getAttribute('aria-modal') === 'true'
                          || (el.className && String(el.className).toLowerCase().match(
                               /(modal|dialog|overlay|backdrop|drawer|popup|lightbox)/)));
                        const score = coverage * 1000 + zScore + (looksModal ? 200 : 0)
                          + (pos === 'fixed' ? 50 : 0);
                        if (score > bestCoverage) {
                          bestCoverage = score;
                          best = {
                            coverage: Math.round(coverage * 1000) / 1000,
                            zIndex: zScore,
                            tag: (el.tagName || '').toLowerCase(),
                            role: el.getAttribute('role') || '',
                            className: (el.className && typeof el.className === 'string')
                              ? el.className.slice(0, 120) : '',
                            looksModal: looksModal,
                            x: Math.round(r.x),
                            y: Math.round(r.y),
                            width: Math.round(r.width),
                            height: Math.round(r.height)
                          };
                        }
                      }
                      return best;
                    }
                    """.formatted(MIN_VIEWPORT_COVERAGE));
            if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
                return (Map<String, Object>) map;
            }
        } catch (RuntimeException ignored) {
            // Page may be navigating; treat as no overlay.
        }
        return null;
    }

    private static boolean tryEscape(Page page) {
        try {
            page.keyboard().press("Escape");
            PageReadinessContract.boundedMicroSettle(page, 120);
            return findTopBlocker(page) == null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean tryCloseControl(Page page) {
        String[] names = {"Close", "Dismiss", "Cancel", "×", "✕", "X"};
        for (String name : names) {
            try {
                Locator byRole = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
                if (byRole.count() > 0 && byRole.first().isVisible()) {
                    byRole.first().click(new Locator.ClickOptions()
                            .setTimeout(DISMISS_TIMEOUT_MS)
                            .setNoWaitAfter(true));
                    PageReadinessContract.boundedMicroSettle(page, 120);
                    if (findTopBlocker(page) == null) {
                        return true;
                    }
                }
            } catch (RuntimeException ignored) {
                // try next strategy
            }
        }
        String[] css = {
                "[aria-label*='close' i]",
                "[aria-label*='dismiss' i]",
                "[title*='close' i]",
                "[data-dismiss]",
                "[data-testid*='close' i]",
                "button.close",
                "[class*='close-button' i]",
                "[class*='modal-close' i]"
        };
        for (String selector : css) {
            try {
                Locator close = page.locator(selector);
                int count = Math.min(close.count(), 5);
                for (int i = 0; i < count; i++) {
                    Locator candidate = close.nth(i);
                    if (!candidate.isVisible()) {
                        continue;
                    }
                    candidate.click(new Locator.ClickOptions()
                            .setTimeout(DISMISS_TIMEOUT_MS)
                            .setNoWaitAfter(true));
                    PageReadinessContract.boundedMicroSettle(page, 120);
                    if (findTopBlocker(page) == null) {
                        return true;
                    }
                }
            } catch (RuntimeException ignored) {
                // try next selector
            }
        }
        return false;
    }

    private static boolean tryBackdropClick(Page page, Map<String, Object> blocker) {
        try {
            // Click near the top-right of the overlay — usually outside modal content, on the dimmer.
            double x = toDouble(blocker.get("x")) + Math.max(toDouble(blocker.get("width")) - 12, 8);
            double y = toDouble(blocker.get("y")) + 12;
            page.mouse().click(x, y);
            PageReadinessContract.boundedMicroSettle(page, 120);
            return findTopBlocker(page) == null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
