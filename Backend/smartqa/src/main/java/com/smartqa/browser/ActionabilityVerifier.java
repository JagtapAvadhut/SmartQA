package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

/**
 * Deterministic pre-action checks. {@code locator.count() > 0} is never sufficient.
 */
public final class ActionabilityVerifier {

    public record Result(
            boolean ok,
            boolean exists,
            boolean visible,
            boolean enabled,
            boolean inViewport,
            boolean covered,
            String reason
    ) {
        public static Result fail(String reason, boolean exists, boolean visible, boolean enabled,
                                  boolean inViewport, boolean covered) {
            return new Result(false, exists, visible, enabled, inViewport, covered, reason);
        }

        public static Result pass(boolean exists, boolean visible, boolean enabled, boolean inViewport) {
            return new Result(true, exists, visible, enabled, inViewport, false, null);
        }
    }

    private ActionabilityVerifier() {
    }

    public static Result verify(Locator locator, String action) {
        if (locator == null) {
            return Result.fail("locator is null", false, false, false, false, false);
        }
        boolean exists;
        try {
            exists = locator.count() > 0;
        } catch (RuntimeException ex) {
            return Result.fail("count failed: " + ex.getMessage(), false, false, false, false, false);
        }
        if (!exists) {
            return Result.fail("element does not exist", false, false, false, false, false);
        }
        boolean visible = safe(() -> locator.first().isVisible(), false);
        boolean enabled = safe(() -> locator.first().isEnabled(), false);
        boolean inViewport = safeInViewport(locator.first());
        boolean covered = looksCovered(locator.first());

        TraceLogger.info("PLAYWRIGHT", "ACTIONABILITY_CHECK", "Pre-action actionability", TraceMeta.of(
                "action", action == null ? "" : action,
                "exists", exists,
                "visible", visible,
                "enabled", enabled,
                "inViewport", inViewport,
                "covered", covered
        ));

        if (!visible) {
            return Result.fail("element not visible", true, false, enabled, inViewport, covered);
        }
        if (!enabled && requiresEnabled(action)) {
            return Result.fail("element not enabled", true, true, false, inViewport, covered);
        }
        if (covered) {
            return Result.fail("element covered by overlay", true, true, enabled, inViewport, true);
        }
        String stale = staleReason(locator.first());
        if (stale != null) {
            return Result.fail(stale, true, true, enabled, inViewport, false);
        }
        if (!geometryStable(locator.first())) {
            return Result.fail("element bounding box unstable", true, true, enabled, inViewport, false);
        }
        return Result.pass(true, true, enabled, inViewport);
    }

    public static void verifyOrThrow(Locator locator, String action) {
        Result result = verify(locator, action);
        if (!result.ok()) {
            ErrorCode code = result.reason() != null && result.reason().toLowerCase().contains("stale")
                    ? ErrorCode.STALE_ELEMENT
                    : ErrorCode.ACTIONABILITY_FAILURE;
            throw new SmartQaException(code, "Actionability failed: " + result.reason());
        }
    }

    private static boolean requiresEnabled(String action) {
        if (action == null) {
            return true;
        }
        String a = action.toLowerCase();
        return !"verify".equals(a) && !"wait".equals(a) && !"hover".equals(a);
    }

    private static boolean safeInViewport(Locator locator) {
        try {
            Object result = locator.evaluate("""
                    el => {
                      if (!el || !el.getBoundingClientRect) return false;
                      const r = el.getBoundingClientRect();
                      if (r.width < 1 || r.height < 1) return false;
                      const vw = window.innerWidth || 1;
                      const vh = window.innerHeight || 1;
                      return r.bottom > 0 && r.right > 0 && r.top < vh && r.left < vw;
                    }
                    """);
            return result instanceof Boolean b && b;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private static boolean looksCovered(Locator locator) {
        try {
            Object result = locator.evaluate("""
                    el => {
                      if (!el || !el.getBoundingClientRect) return false;
                      const r = el.getBoundingClientRect();
                      const x = Math.min(Math.max(r.left + r.width / 2, 0), (window.innerWidth || 1) - 1);
                      const y = Math.min(Math.max(r.top + r.height / 2, 0), (window.innerHeight || 1) - 1);
                      const top = document.elementFromPoint(x, y);
                      if (!top) return false;
                      return !(el === top || el.contains(top) || top.contains(el));
                    }
                    """);
            return result instanceof Boolean b && b;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String staleReason(Locator locator) {
        try {
            Object attached = locator.evaluate("el => !!(el && el.isConnected)");
            if (Boolean.FALSE.equals(attached)) {
                return "stale element: not attached";
            }
            return null;
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (msg.contains("not attached") || msg.contains("detached") || msg.contains("destroyed")) {
                return "stale element: " + truncate(ex.getMessage(), 80);
            }
            return null;
        }
    }

    private static boolean geometryStable(Locator locator) {
        try {
            Object first = locator.evaluate("""
                    el => {
                      if (!el || !el.getBoundingClientRect) return null;
                      const r = el.getBoundingClientRect();
                      return [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)].join(',');
                    }
                    """);
            locator.evaluate("""
                    el => new Promise(resolve => {
                      requestAnimationFrame(() => requestAnimationFrame(() => resolve(true)));
                    })
                    """);
            Object second = locator.evaluate("""
                    el => {
                      if (!el || !el.getBoundingClientRect) return null;
                      const r = el.getBoundingClientRect();
                      return [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)].join(',');
                    }
                    """);
            if (first == null || second == null) {
                return true;
            }
            return String.valueOf(first).equals(String.valueOf(second));
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            return !(msg.contains("not attached") || msg.contains("detached") || msg.contains("destroyed"));
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean safe(BooleanSupplier supplier, boolean fallback) {
        try {
            return supplier.getAsBoolean();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
