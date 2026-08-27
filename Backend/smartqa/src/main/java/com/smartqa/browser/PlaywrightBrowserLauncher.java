package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.ViewportSize;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single place for Playwright launch / context / viewport / page-zoom policy.
 *
 * <ul>
 *   <li>Headed + maximize: Chromium {@code --start-maximized}, no fixed Playwright viewport</li>
 *   <li>Headed page zoom: Chromium Zoom UI via extension {@code chrome.tabs.setZoom}
 *       ({@code smartqa.browser.zoom-percent}, default 50) — not window resize</li>
 *   <li>Headless: deterministic configurable viewport (no maximize / no Chrome Zoom UI)</li>
 * </ul>
 */
public final class PlaywrightBrowserLauncher {

    public record Session(
            Browser browser,
            BrowserContext context,
            Page page,
            boolean headless,
            boolean maximizeRequested,
            int zoomPercent,
            BrowserViewportEvidence viewport,
            BrowserPageZoom.ZoomEvidence zoom
    ) {
        /**
         * Owner: {@code PlaywrightBrowserExecutionProvider.execute()} (locator capture).
         * Lifetime: {@link PlaywrightBrowserLauncher#open} until terminal success, terminal failure,
         * explicit Stop, or process shutdown. SSE disconnect is not a release condition.
         */
        public void close() {
            close(BrowserLifecycle.CLOSE_PLAYWRIGHT_DISPOSE, "Session.close");
        }

        public void close(String reason, String caller) {
            closeQuietly(this, reason, caller);
        }
    }

    private PlaywrightBrowserLauncher() {
    }

    public static boolean resolveHeadless(SmartQaProperties.Browser config, Boolean headlessOverride) {
        if (headlessOverride != null) {
            return headlessOverride;
        }
        return config == null || config.isHeadless();
    }

    public static boolean resolveMaximize(SmartQaProperties.Browser config, boolean headless) {
        if (headless) {
            return false;
        }
        return config == null || config.isMaximizeHeaded();
    }

    public static String resolveBrowserType(SmartQaProperties.Browser config) {
        if (config == null || config.getType() == null || config.getType().isBlank()) {
            return "chromium";
        }
        return config.getType().trim().toLowerCase(Locale.ROOT);
    }

    public static BrowserType.LaunchOptions launchOptions(
            SmartQaProperties.Browser config,
            Boolean headlessOverride) {
        boolean headless = resolveHeadless(config, headlessOverride);
        boolean maximize = resolveMaximize(config, headless);
        String type = resolveBrowserType(config);
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
        List<String> args = new ArrayList<>();
        args.add("--disable-popup-blocking");
        if (maximize && "chromium".equals(type)) {
            args.add("--start-maximized");
        }
        options.setArgs(args);
        return options;
    }

    public static Browser.NewContextOptions contextOptions(
            SmartQaProperties.Browser config,
            boolean headless,
            boolean maximizeRequested) {
        Browser.NewContextOptions options = new Browser.NewContextOptions();
        if (headless) {
            int width = config == null ? 1280 : Math.max(320, config.getHeadlessViewportWidth());
            int height = config == null ? 720 : Math.max(240, config.getHeadlessViewportHeight());
            options.setViewportSize(width, height);
        } else if (maximizeRequested) {
            // Disable Playwright's default 1280x720 viewport so content uses the real window.
            options.setViewportSize((ViewportSize) null);
        }
        return options;
    }

    public static Browser launch(Playwright playwright, SmartQaProperties.Browser config, Boolean headlessOverride) {
        BrowserType.LaunchOptions launchOptions = launchOptions(config, headlessOverride);
        return switch (resolveBrowserType(config)) {
            case "firefox" -> playwright.firefox().launch(launchOptions);
            case "webkit" -> playwright.webkit().launch(launchOptions);
            default -> playwright.chromium().launch(launchOptions);
        };
    }

    public static Session open(Playwright playwright, SmartQaProperties.Browser config, Boolean headlessOverride) {
        boolean headless = resolveHeadless(config, headlessOverride);
        boolean maximize = resolveMaximize(config, headless);
        int zoomPercent = BrowserPageZoom.resolveZoomPercent(config, headless);
        String type = resolveBrowserType(config);

        BrowserLifecycle.info(BrowserLifecycle.SESSION_ACQUIRE, "Acquiring browser session",
                TraceMeta.of("owner", "PlaywrightBrowserLauncher", "browserType", type, "headless", headless));
        BrowserLifecycle.info(BrowserLifecycle.BROWSER_CREATE_STARTED, "Launching browser",
                TraceMeta.of("browserType", type, "headless", headless, "requestedZoom", zoomPercent));

        if (!headless && zoomPercent != 100 && "chromium".equals(type)) {
            try {
                Session zoomed = openHeadedWithZoomExtension(playwright, config, maximize, zoomPercent);
                if (isSessionAlive(zoomed)) {
                    attachLifecycleListeners(zoomed);
                    BrowserLifecycle.info(BrowserLifecycle.BROWSER_CREATED, "Headed Chromium with zoom extension",
                            BrowserLifecycle.identity(zoomed.browser(), zoomed.context(), zoomed.page(),
                                    "PlaywrightBrowserLauncher", null, null));
                    return zoomed;
                }
                closeQuietly(zoomed, BrowserLifecycle.CLOSE_ZOOM_EXTENSION_DEAD, "PlaywrightBrowserLauncher.open");
                BrowserLifecycle.warn("BROWSER_FALLBACK_STANDARD_LAUNCH",
                        "Zoom-extension session was not alive; launching a standard browser",
                        TraceMeta.of("reason", "zoom-extension-dead"));
            } catch (RuntimeException ex) {
                if (!BrowserLifecycle.isClosedTargetFailure(ex) && !BrowserLifecycle.isRecoverableLaunchFailure(ex)) {
                    throw ex;
                }
                BrowserLifecycle.warn("BROWSER_FALLBACK_STANDARD_LAUNCH",
                        "Zoom-extension launch failed; launching a standard browser",
                        TraceMeta.of("reason", ex.getMessage() == null ? "" : ex.getMessage()));
            }
        }

        Session standard = openStandard(playwright, config, headlessOverride, headless, maximize, zoomPercent, type);
        attachLifecycleListeners(standard);
        BrowserLifecycle.info(BrowserLifecycle.BROWSER_CREATED, "Browser launched",
                BrowserLifecycle.identity(standard.browser(), standard.context(), standard.page(),
                        "PlaywrightBrowserLauncher", null, null));
        return standard;
    }

    private static Session openStandard(
            Playwright playwright,
            SmartQaProperties.Browser config,
            Boolean headlessOverride,
            boolean headless,
            boolean maximize,
            int zoomPercent,
            String type) {
        BrowserLifecycle.info(BrowserLifecycle.CONTEXT_CREATE_STARTED, "Creating browser context");
        Browser browser = launch(playwright, config, headlessOverride);
        BrowserContext context = browser.newContext(contextOptions(config, headless, maximize));
        BrowserLifecycle.info(BrowserLifecycle.CONTEXT_CREATED, "Browser context created");
        BrowserLifecycle.info(BrowserLifecycle.PAGE_CREATE_STARTED, "Creating page");
        Page page = context.newPage();
        BrowserLifecycle.info(BrowserLifecycle.PAGE_CREATED, "Page created");
        BrowserPageZoom.ZoomEvidence zoomEvidence;
        if (!headless && zoomPercent != 100) {
            BrowserPageZoom.install(page, zoomPercent);
            zoomEvidence = new BrowserPageZoom.ZoomEvidence(
                    zoomPercent,
                    zoomPercent,
                    "chrome-keyboard-zoom-deferred",
                    page.url(),
                    "",
                    0,
                    0,
                    0,
                    0,
                    1.0,
                    true);
            BrowserPageZoom.emitConfigured(zoomEvidence);
        } else {
            zoomEvidence = new BrowserPageZoom.ZoomEvidence(
                    zoomPercent,
                    zoomPercent,
                    headless ? "headless-noop" : "zoom-100",
                    page.url(),
                    "",
                    0,
                    0,
                    0,
                    0,
                    1.0,
                    true);
            BrowserPageZoom.emitConfigured(zoomEvidence);
        }
        BrowserViewportEvidence evidence = captureViewport(page, type, headless, maximize);
        emitViewportReady(evidence);
        return new Session(browser, context, page, headless, maximize, zoomPercent, evidence, zoomEvidence);
    }

    private static Session openHeadedWithZoomExtension(
            Playwright playwright,
            SmartQaProperties.Browser config,
            boolean maximize,
            int zoomPercent) {
        Path extensionDir = ZoomExtensionSupport.ensureExtracted();
        Path userDataDir;
        try {
            userDataDir = Files.createTempDirectory("smartqa-chrome-profile-");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create Chromium user-data dir for zoom", ex);
        }
        List<String> args = new ArrayList<>(ZoomExtensionSupport.chromiumExtensionArgs(extensionDir));
        if (maximize) {
            args.add("--start-maximized");
        }
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(false)
                .setArgs(args);
        if (maximize) {
            options.setViewportSize((ViewportSize) null);
        } else {
            int width = config == null ? 1280 : Math.max(320, config.getHeadlessViewportWidth());
            int height = config == null ? 720 : Math.max(240, config.getHeadlessViewportHeight());
            options.setViewportSize(width, height);
        }
        BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);
        Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        BrowserPageZoom.install(page, zoomPercent);
        // http(s) page so the extension content script can inject and set Chrome Zoom.
        // Never abort a real test because this warmup probe failed (network flap, DNS, etc.).
        warmHttpsPageForZoom(page);
        if (!isPageAlive(page)) {
            throw new IllegalStateException("Target page, context or browser has been closed");
        }
        BrowserPageZoom.ZoomEvidence zoomEvidence = BrowserPageZoom.apply(page, zoomPercent);
        Browser browser = context.browser();
        BrowserViewportEvidence evidence = captureViewport(page, "chromium", false, maximize);
        emitViewportReady(evidence);
        return new Session(browser, context, page, false, maximize, zoomPercent, evidence, zoomEvidence);
    }

    public static BrowserViewportEvidence captureViewport(
            Page page,
            String browser,
            boolean headless,
            boolean maximizeRequested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) page.evaluate("""
                () => ({
                  innerWidth: window.innerWidth,
                  innerHeight: window.innerHeight,
                  outerWidth: window.outerWidth,
                  outerHeight: window.outerHeight,
                  screenWidth: window.screen.width,
                  screenHeight: window.screen.height,
                  availableScreenWidth: window.screen.availWidth,
                  availableScreenHeight: window.screen.availHeight,
                  devicePixelRatio: window.devicePixelRatio
                })
                """);
        return new BrowserViewportEvidence(
                browser,
                headless,
                maximizeRequested,
                toInt(raw.get("innerWidth")),
                toInt(raw.get("innerHeight")),
                toInt(raw.get("outerWidth")),
                toInt(raw.get("outerHeight")),
                toInt(raw.get("screenWidth")),
                toInt(raw.get("screenHeight")),
                toInt(raw.get("availableScreenWidth")),
                toInt(raw.get("availableScreenHeight")),
                toDouble(raw.get("devicePixelRatio"))
        );
    }

    public static void emitViewportReady(BrowserViewportEvidence evidence) {
        if (evidence == null) {
            return;
        }
        TraceLogger.info(
                "BROWSER",
                "BROWSER_VIEWPORT_READY",
                "Live browser viewport metrics captured",
                evidence.toTraceMeta()
        );
    }

    /**
     * Source fragment used by generated Playwright tests so validation/execution share this policy.
     */
    public static String generatedLaunchSnippet(String indent) {
        String body = """
                boolean headless = Boolean.parseBoolean(System.getProperty("smartqa.browser.headless", "true"));
                boolean maximizeHeaded = Boolean.parseBoolean(System.getProperty("smartqa.browser.maximize-headed", "true"));
                int zoomPercent = Integer.parseInt(System.getProperty("smartqa.browser.zoom-percent", "50"));
                int headlessWidth = Integer.parseInt(System.getProperty("smartqa.browser.headless-viewport-width", "1280"));
                int headlessHeight = Integer.parseInt(System.getProperty("smartqa.browser.headless-viewport-height", "720"));
                BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
                if (!headless && maximizeHeaded) {
                    launchOptions.setArgs(java.util.List.of("--start-maximized"));
                }
                Browser browser = playwright.chromium().launch(launchOptions);
                Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
                if (headless) {
                    contextOptions.setViewportSize(headlessWidth, headlessHeight);
                } else if (maximizeHeaded) {
                    contextOptions.setViewportSize((com.microsoft.playwright.options.ViewportSize) null);
                }
                BrowserContext context = browser.newContext(contextOptions);
                Page page = context.newPage();
                if (!headless && zoomPercent != 100) {
                    page.onFrameNavigated(frame -> {
                        if (frame == page.mainFrame()) {
                            try {
                                page.bringToFront();
                                page.keyboard().press("ControlOrMeta+0");
                                // Chrome presets from 100% → 50%: 90,80,75,67,50 (5 steps)
                                int stepsFrom100To50 = 5;
                                int steps = zoomPercent == 50 ? stepsFrom100To50 : Math.max(0, Math.round((100 - zoomPercent) / 10.0f));
                                for (int i = 0; i < steps; i++) {
                                    page.keyboard().press("ControlOrMeta+-");
                                }
                            } catch (RuntimeException ignored) {
                            }
                        }
                    });
                }
                """;
        return indent + body.replace("\n", "\n" + indent).stripTrailing();
    }

    /**
     * Zoom extension needs an http(s) document. This is a probe, not the application under test.
     * Transient network errors must not fail Generate &amp; Validate.
     */
    static boolean warmHttpsPageForZoom(Page page) {
        if (!isPageAlive(page)) {
            BrowserLifecycle.warn("ZOOM_WARMUP_SKIPPED", "Page already closed before zoom warmup",
                    TraceMeta.of("closeReason", BrowserLifecycle.CLOSE_TARGET_CLOSED, "closeCaller", "warmHttpsPageForZoom"));
            return false;
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                page.navigate("https://example.com", new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(15_000));
                return isPageAlive(page);
            } catch (RuntimeException ex) {
                last = ex;
                if (BrowserLifecycle.isClosedTargetFailure(ex)) {
                    BrowserLifecycle.warn("ZOOM_WARMUP_SKIPPED", "Zoom warmup hit a closed page; not retrying",
                            TraceMeta.of("attempt", attempt, "closeReason", BrowserLifecycle.CLOSE_TARGET_CLOSED,
                                    "closeCaller", "warmHttpsPageForZoom"));
                    return false;
                }
                if (!isTransientNavigationFailure(ex.getMessage())) {
                    TraceLogger.warn("BROWSER", "ZOOM_WARMUP_SKIPPED", "Zoom warmup navigation failed; continuing without aborting the test",
                            TraceMeta.of("attempt", attempt, "error", ex.getMessage() == null ? "" : ex.getMessage()));
                    return isPageAlive(page);
                }
                TraceLogger.warn("BROWSER", "ZOOM_WARMUP_RETRY", "Transient zoom warmup failure",
                        TraceMeta.of("attempt", attempt, "error", ex.getMessage() == null ? "" : ex.getMessage()));
                try {
                    Thread.sleep(400L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return isPageAlive(page);
                }
            }
        }
        TraceLogger.warn("BROWSER", "ZOOM_WARMUP_SKIPPED", "Zoom warmup exhausted retries; continuing",
                TraceMeta.of("error", last == null || last.getMessage() == null ? "" : last.getMessage()));
        return isPageAlive(page);
    }

    static boolean isTransientNavigationFailure(String message) {
        if (BrowserLifecycle.isClosedTargetFailure(message)) {
            return false;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("err_network_changed")
                || lower.contains("err_internet_disconnected")
                || lower.contains("err_connection_reset")
                || lower.contains("err_connection_refused")
                || lower.contains("err_name_not_resolved")
                || lower.contains("err_timed_out")
                || lower.contains("timeout")
                || lower.contains("net::err_aborted");
    }

    static boolean isPageAlive(Page page) {
        if (page == null) {
            return false;
        }
        try {
            if (page.isClosed()) {
                return false;
            }
            BrowserContext ctx = page.context();
            if (ctx != null) {
                Browser browser = ctx.browser();
                if (browser != null && !browser.isConnected()) {
                    return false;
                }
            }
            page.evaluate("() => 1");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static boolean isSessionAlive(Session session) {
        return session != null && isPageAlive(session.page());
    }

    static void attachLifecycleListeners(Session session) {
        if (session == null) {
            return;
        }
        Page page = session.page();
        if (page != null) {
            page.onClose(closed -> BrowserLifecycle.info(BrowserLifecycle.PAGE_CLOSED, "Page close event",
                    BrowserLifecycle.identity(session.browser(), session.context(), page,
                            "PlaywrightBrowserLauncher", "page-close-event", "page.onClose")));
        }
        if (session.context() != null) {
            session.context().onClose(closed -> BrowserLifecycle.info(BrowserLifecycle.CONTEXT_CLOSED, "Context close event",
                    BrowserLifecycle.identity(session.browser(), session.context(), session.page(),
                            "PlaywrightBrowserLauncher", "context-close-event", "context.onClose")));
        }
        if (session.browser() != null) {
            session.browser().onDisconnected(disconnected -> BrowserLifecycle.info(BrowserLifecycle.BROWSER_CLOSED, "Browser disconnected",
                    BrowserLifecycle.identity(session.browser(), session.context(), session.page(),
                            "PlaywrightBrowserLauncher", "browser-disconnected", "browser.onDisconnected")));
        }
    }

    static void closeQuietly(Session session, String reason, String caller) {
        if (session == null) {
            return;
        }
        java.util.Map<String, Object> identity = BrowserLifecycle.identity(
                session.browser(), session.context(), session.page(),
                "PlaywrightBrowserLauncher.Session", reason, caller);
        BrowserLifecycle.info(BrowserLifecycle.SESSION_RELEASE, "Releasing browser session", identity);
        try {
            if (session.page() != null) {
                BrowserLifecycle.info(BrowserLifecycle.PAGE_CLOSE_REQUESTED, "Page close requested", identity);
                if (!session.page().isClosed()) {
                    session.page().close();
                }
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (session.context() != null) {
                BrowserLifecycle.info(BrowserLifecycle.CONTEXT_CLOSE_REQUESTED, "Context close requested", identity);
                session.context().close();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (session.browser() != null) {
                BrowserLifecycle.info(BrowserLifecycle.BROWSER_CLOSE_REQUESTED, "Browser close requested", identity);
                session.browser().close();
            }
        } catch (RuntimeException ignored) {
        }
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
}
