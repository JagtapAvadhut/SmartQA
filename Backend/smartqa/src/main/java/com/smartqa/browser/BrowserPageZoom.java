package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Applies Chromium <em>page zoom</em> (Chrome ⋮ → Zoom), not OS window sizing and not viewport emulation.
 *
 * <p>Primary mechanism: SmartQA Chromium extension calling {@code chrome.tabs.setZoom} (updates the Chrome Zoom UI).
 * Fallback: native zoom keyboard shortcuts when the extension bridge is unavailable.
 */
public final class BrowserPageZoom {

    public static final int DEFAULT_HEADED_ZOOM_PERCENT = 50;

    /** Absolute tolerance when validating effective zoom against the request (percentage points). */
    public static final double ZOOM_TOLERANCE_PERCENT = 8.0;

    private static final Map<Page, Integer> INSTALLED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Page, Boolean> APPLYING = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Page, String> LAST_ZOOM_ORIGIN = Collections.synchronizedMap(new WeakHashMap<>());

    private BrowserPageZoom() {
    }

    public record ZoomEvidence(
            int requestedZoomPercent,
            double effectiveZoomPercent,
            String method,
            String pageUrl,
            String pageTitle,
            int viewportWidth,
            int viewportHeight,
            int browserWindowWidth,
            int browserWindowHeight,
            double devicePixelRatio,
            boolean approximatelyRequested
    ) {
        public Map<String, Object> toTraceMeta() {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("requestedZoom", requestedZoomPercent);
            meta.put("actualZoom", Math.round(effectiveZoomPercent * 10.0) / 10.0);
            meta.put("effectiveZoom", Math.round(effectiveZoomPercent * 10.0) / 10.0);
            meta.put("method", method);
            meta.put("pageUrl", pageUrl);
            meta.put("pageTitle", pageTitle);
            meta.put("viewportWidth", viewportWidth);
            meta.put("viewportHeight", viewportHeight);
            meta.put("browserWindowWidth", browserWindowWidth);
            meta.put("browserWindowHeight", browserWindowHeight);
            meta.put("devicePixelRatio", devicePixelRatio);
            meta.put("zoomTolerancePercent", ZOOM_TOLERANCE_PERCENT);
            meta.put("approximatelyRequested", approximatelyRequested);
            return meta;
        }
    }

    public static int resolveZoomPercent(SmartQaProperties.Browser config, boolean headless) {
        if (headless) {
            return 100;
        }
        if (config == null) {
            return DEFAULT_HEADED_ZOOM_PERCENT;
        }
        return normalizePercent(config.getZoomPercent());
    }

    public static void install(Page page, int zoomPercent) {
        if (page == null) {
            return;
        }
        int normalized = normalizePercent(zoomPercent);
        INSTALLED.put(page, normalized);
        page.onFrameNavigated(frame -> {
            if (frame == null || page.mainFrame() == null || frame != page.mainFrame()) {
                return;
            }
            Integer installed = INSTALLED.get(page);
            if (installed == null || installed == 100) {
                return;
            }
            String origin = originOf(page.url());
            String previous = LAST_ZOOM_ORIGIN.get(page);
            if (origin != null && origin.equals(previous)) {
                // Chrome zoom is per-origin; skip SPA/same-origin reloads to avoid interrupting actions.
                return;
            }
            try {
                apply(page, installed);
                if (origin != null) {
                    LAST_ZOOM_ORIGIN.put(page, origin);
                }
            } catch (RuntimeException ignored) {
                // Navigation races — next stable load will retry.
            }
        });
    }

    public static ZoomEvidence apply(Page page, int requestedZoomPercent) {
        int requested = normalizePercent(requestedZoomPercent);
        if (page == null) {
            return emptyEvidence(requested, "none");
        }
        if (BrowserLifecycle.pageLooksClosed(page)) {
            return emptyEvidence(requested, "page-closed");
        }
        if (Boolean.TRUE.equals(APPLYING.putIfAbsent(page, Boolean.TRUE))) {
            Metrics current = readMetrics(page);
            return new ZoomEvidence(
                    requested, requested, "reentrant-skip",
                    current.url, current.title, current.innerWidth, current.innerHeight,
                    current.outerWidth, current.outerHeight, current.devicePixelRatio, true);
        }
        try {
            Double extensionZoom = tryExtensionZoom(page, requested / 100.0);
            String method;
            double effective;
            if (extensionZoom != null && extensionZoom > 0) {
                method = "chrome.tabs.setZoom";
                effective = extensionZoom * 100.0;
            } else {
                method = "chrome-keyboard-zoom";
                effective = applyKeyboardZoom(page, requested);
            }
            settle(page);
            Metrics after = readMetrics(page);
            boolean approx = Math.abs(effective - requested) <= ZOOM_TOLERANCE_PERCENT;
            ZoomEvidence evidence = new ZoomEvidence(
                    requested,
                    effective,
                    method,
                    after.url,
                    after.title,
                    after.innerWidth,
                    after.innerHeight,
                    after.outerWidth,
                    after.outerHeight,
                    after.devicePixelRatio,
                    approx
            );
            emitConfigured(evidence);
            String origin = originOf(after.url);
            if (origin != null) {
                LAST_ZOOM_ORIGIN.put(page, origin);
            }
            return evidence;
        } catch (RuntimeException ex) {
            if (BrowserLifecycle.isClosedTargetFailure(ex) || BrowserLifecycle.pageLooksClosed(page)) {
                return emptyEvidence(requested, "page-closed");
            }
            throw ex;
        } finally {
            APPLYING.remove(page);
        }
    }

    private static String originOf(String url) {
        if (url == null || url.isBlank() || url.startsWith("about:") || url.startsWith("chrome:")) {
            return url;
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return url;
            }
            int port = uri.getPort();
            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        } catch (RuntimeException ex) {
            return url;
        }
    }

    public static void emitConfigured(ZoomEvidence evidence) {
        if (evidence == null) {
            return;
        }
        TraceLogger.info(
                "BROWSER",
                "BROWSER_ZOOM_CONFIGURED",
                "Chromium page zoom configured",
                evidence.toTraceMeta()
        );
    }

    public static boolean isApproximately(int requestedZoomPercent, double effectiveZoomPercent) {
        return Math.abs(effectiveZoomPercent - normalizePercent(requestedZoomPercent)) <= ZOOM_TOLERANCE_PERCENT;
    }

    static int normalizePercent(int percent) {
        if (percent < 25) {
            return 25;
        }
        if (percent > 500) {
            return 500;
        }
        return percent;
    }

    @SuppressWarnings("unchecked")
    private static Double tryExtensionZoom(Page page, double zoomFactor) {
        try {
            // Content script must be present (http/https/file pages). Seed pages use setContent first.
            String requestId = UUID.randomUUID().toString();
            Object raw = page.evaluate("""
                    ({ factor, requestId }) => new Promise((resolve) => {
                      const timer = setTimeout(() => resolve({ ok: false, error: 'timeout' }), 2500);
                      function onMessage(event) {
                        if (event.source !== window) return;
                        const data = event.data;
                        if (!data || data.source !== 'smartqa-zoom-result' || data.requestId !== requestId) return;
                        window.removeEventListener('message', onMessage);
                        clearTimeout(timer);
                        resolve(data);
                      }
                      window.addEventListener('message', onMessage);
                      window.postMessage({
                        source: 'smartqa-zoom',
                        type: 'SET_ZOOM',
                        factor,
                        requestId
                      }, '*');
                    })
                    """, Map.of("factor", zoomFactor, "requestId", requestId));
            if (!(raw instanceof Map<?, ?> map)) {
                return null;
            }
            Object ok = map.get("ok");
            if (!(ok instanceof Boolean bool) || !bool) {
                return null;
            }
            Object zoom = map.get("zoom");
            if (zoom instanceof Number number) {
                return number.doubleValue();
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static double applyKeyboardZoom(Page page, int requestedPercent) {
        try {
            page.bringToFront();
            page.locator("body").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(2000));
        } catch (RuntimeException ignored) {
            // Focus best-effort.
        }
        Metrics before = readMetrics(page);
        page.keyboard().press("Control+0");
        settle(page);
        // Chrome presets from 100% down to 50%: 90, 80, 75, 67, 50
        int[] presets = {25, 33, 50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300, 400, 500};
        int from = indexOf(presets, 100);
        int to = nearestIndex(presets, requestedPercent);
        if (to < from) {
            for (int i = 0; i < from - to; i++) {
                page.keyboard().press("Control+Minus");
            }
        } else if (to > from) {
            for (int i = 0; i < to - from; i++) {
                page.keyboard().press("Control+Equal");
            }
        }
        settle(page);
        Metrics after = readMetrics(page);
        if (before.innerWidth > 0 && after.innerWidth > 0 && before.innerWidth != after.innerWidth) {
            return 100.0 * ((double) before.innerWidth / (double) after.innerWidth);
        }
        return 100.0;
    }

    private static int indexOf(int[] presets, int value) {
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == value) {
                return i;
            }
        }
        return 7; // 100
    }

    private static int nearestIndex(int[] presets, int percent) {
        int best = 0;
        int bestDelta = Math.abs(presets[0] - percent);
        for (int i = 1; i < presets.length; i++) {
            int delta = Math.abs(presets[i] - percent);
            if (delta < bestDelta) {
                best = i;
                bestDelta = delta;
            }
        }
        return best;
    }

    private static void settle(Page page) {
        try {
            page.waitForTimeout(150);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    @SuppressWarnings("unchecked")
    private static Metrics readMetrics(Page page) {
        Map<String, Object> raw = (Map<String, Object>) page.evaluate("""
                () => ({
                  innerWidth: window.innerWidth,
                  innerHeight: window.innerHeight,
                  outerWidth: window.outerWidth,
                  outerHeight: window.outerHeight,
                  devicePixelRatio: window.devicePixelRatio,
                  url: location.href,
                  title: document.title || ''
                })
                """);
        return new Metrics(
                toInt(raw.get("innerWidth")),
                toInt(raw.get("innerHeight")),
                toInt(raw.get("outerWidth")),
                toInt(raw.get("outerHeight")),
                toDouble(raw.get("devicePixelRatio")),
                String.valueOf(raw.getOrDefault("url", "")),
                String.valueOf(raw.getOrDefault("title", ""))
        );
    }

    private static ZoomEvidence emptyEvidence(int requested, String method) {
        return new ZoomEvidence(requested, requested, method, "", "", 0, 0, 0, 0, 1.0, false);
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private record Metrics(
            int innerWidth,
            int innerHeight,
            int outerWidth,
            int outerHeight,
            double devicePixelRatio,
            String url,
            String title
    ) {
    }
}
