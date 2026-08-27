package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.event.RunCorrelation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Structured browser lifetime events. One owner per Playwright page/context/browser:
 * {@code PlaywrightBrowserExecutionProvider.execute()} for locator capture, and a separate
 * isolated JVM for generated-test validation/execution.
 *
 * <p>Release is allowed on explicit Stop, terminal success/failure, or process shutdown.
 * SSE disconnect, generation-stream completion, and AI-call completion are not release conditions.
 */
public final class BrowserLifecycle {

    public static final String COMPONENT = "BROWSER_LIFECYCLE";

    public static final String BROWSER_CREATE_STARTED = "BROWSER_CREATE_STARTED";
    public static final String BROWSER_CREATED = "BROWSER_CREATED";
    public static final String CONTEXT_CREATE_STARTED = "CONTEXT_CREATE_STARTED";
    public static final String CONTEXT_CREATED = "CONTEXT_CREATED";
    public static final String PAGE_CREATE_STARTED = "PAGE_CREATE_STARTED";
    public static final String PAGE_CREATED = "PAGE_CREATED";
    public static final String BROWSER_OPERATION_STARTED = "BROWSER_OPERATION_STARTED";
    public static final String BROWSER_OPERATION_FINISHED = "BROWSER_OPERATION_FINISHED";
    public static final String PAGE_CLOSE_REQUESTED = "PAGE_CLOSE_REQUESTED";
    public static final String PAGE_CLOSED = "PAGE_CLOSED";
    public static final String CONTEXT_CLOSE_REQUESTED = "CONTEXT_CLOSE_REQUESTED";
    public static final String CONTEXT_CLOSED = "CONTEXT_CLOSED";
    public static final String BROWSER_CLOSE_REQUESTED = "BROWSER_CLOSE_REQUESTED";
    public static final String BROWSER_CLOSED = "BROWSER_CLOSED";
    public static final String SESSION_ACQUIRE = "SESSION_ACQUIRE";
    public static final String SESSION_RELEASE = "SESSION_RELEASE";
    public static final String EXECUTION_START = "EXECUTION_START";
    public static final String EXECUTION_END = "EXECUTION_END";
    public static final String GENERATION_START = "GENERATION_START";
    public static final String GENERATION_END = "GENERATION_END";

    public static final String CLOSE_EXECUTION_COMPLETE = "EXECUTION_COMPLETE";
    public static final String CLOSE_EXECUTION_FAILED = "EXECUTION_FAILED";
    public static final String CLOSE_EXPLICIT_STOP = "EXPLICIT_STOP";
    public static final String CLOSE_TARGET_CLOSED = "TARGET_CLOSED";
    public static final String CLOSE_PLAYWRIGHT_DISPOSE = "PLAYWRIGHT_DISPOSE";
    public static final String CLOSE_ZOOM_EXTENSION_DEAD = "ZOOM_EXTENSION_DEAD";
    public static final String CLOSE_FALLBACK = "FALLBACK_REPLACEMENT";

    private BrowserLifecycle() {
    }

    public static void info(String event, String message) {
        info(event, message, correlation());
    }

    public static void info(String event, String message, Map<String, Object> extra) {
        TraceLogger.info(COMPONENT, event, message, merge(correlation(), extra));
    }

    public static void warn(String event, String message, Map<String, Object> extra) {
        TraceLogger.warn(COMPONENT, event, message, merge(correlation(), extra));
    }

    public static Map<String, Object> correlation() {
        return correlation(null, null, null);
    }

    public static Map<String, Object> correlation(String stepId, Integer attempt, Integer stepNumber) {
        Map<String, Object> meta = new LinkedHashMap<>();
        String traceId = TraceContext.current();
        if (traceId != null && !traceId.isBlank()) {
            meta.put("traceId", traceId);
        }
        putId(meta, "pipelineRunId", RunCorrelation.pipelineRunId());
        putId(meta, "generationRunId", RunCorrelation.generationRunId());
        putId(meta, "executionRunId", RunCorrelation.testCaseId());
        if (stepId != null && !stepId.isBlank()) {
            meta.put("stepId", stepId);
        }
        if (attempt != null) {
            meta.put("attempt", attempt);
        }
        if (stepNumber != null) {
            meta.put("stepNumber", stepNumber);
        }
        return meta;
    }

    public static Map<String, Object> identity(
            Object browser,
            Object context,
            Object page,
            String owner,
            String closeReason,
            String closeCaller) {
        Map<String, Object> meta = correlation();
        meta.put("browserId", identityOf(browser));
        meta.put("contextId", identityOf(context));
        meta.put("pageId", identityOf(page));
        if (owner != null) {
            meta.put("owner", owner);
        }
        if (closeReason != null) {
            meta.put("closeReason", closeReason);
        }
        if (closeCaller != null) {
            meta.put("closeCaller", closeCaller);
        }
        return meta;
    }

    public static String identityOf(Object resource) {
        if (resource == null) {
            return "";
        }
        return resource.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(resource));
    }

    public static boolean isClosedTargetFailure(Throwable error) {
        if (error == null) {
            return false;
        }
        if (isClosedTargetFailure(error.getMessage())) {
            return true;
        }
        String name = error.getClass().getSimpleName();
        if (name != null && name.toLowerCase(Locale.ROOT).contains("targetclosed")) {
            return true;
        }
        return isClosedTargetFailure(error.getCause());
    }

    public static boolean isClosedTargetFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("targetclosederror")
                || lower.contains("has been closed")
                || lower.contains("browser has been closed")
                || lower.contains("context has been closed")
                || lower.contains("page has been closed")
                || lower.contains("target page, context or browser");
    }

    public static boolean isRecoverableLaunchFailure(Throwable error) {
        if (isClosedTargetFailure(error)) {
            return true;
        }
        if (error == null || error.getMessage() == null) {
            return false;
        }
        String lower = error.getMessage().toLowerCase(Locale.ROOT);
        return lower.contains("load-extension")
                || lower.contains("persistent")
                || lower.contains("user-data-dir")
                || lower.contains("browser has been disconnected")
                || lower.contains("browser closed")
                || (lower.contains("chromium") && lower.contains("crash"));
    }

    public static String closeReasonFor(Throwable error) {
        if (error == null) {
            return CLOSE_EXECUTION_COMPLETE;
        }
        String simple = error.getClass().getSimpleName();
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (simple.contains("Cancelled") || message.contains("cancelled by user") || message.contains("execution cancelled")) {
            return CLOSE_EXPLICIT_STOP;
        }
        if (isClosedTargetFailure(error)) {
            return CLOSE_TARGET_CLOSED;
        }
        return CLOSE_EXECUTION_FAILED;
    }

    public static boolean pageLooksClosed(Page page) {
        if (page == null) {
            return true;
        }
        try {
            return page.isClosed();
        } catch (RuntimeException ex) {
            return true;
        }
    }

    public static boolean contextLooksClosed(BrowserContext context) {
        if (context == null) {
            return true;
        }
        try {
            return context.pages() == null;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    public static boolean browserLooksClosed(Browser browser) {
        if (browser == null) {
            return false;
        }
        try {
            return !browser.isConnected();
        } catch (RuntimeException ex) {
            return true;
        }
    }

    public static String classifyClosedResource(Page page, BrowserContext context, Browser browser) {
        if (pageLooksClosed(page) && (browser == null || !browserLooksClosed(browser))
                && (context == null || !contextLooksClosed(context))) {
            return PAGE_CLOSED;
        }
        if (contextLooksClosed(context) && (browser == null || !browserLooksClosed(browser))) {
            return CONTEXT_CLOSED;
        }
        if (browserLooksClosed(browser)) {
            return BROWSER_CLOSED;
        }
        if (pageLooksClosed(page)) {
            return PAGE_CLOSED;
        }
        return "RESOURCE_ALIVE";
    }

    private static void putId(Map<String, Object> meta, String key, UUID id) {
        if (id != null) {
            meta.put(key, id.toString());
        }
    }

    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        Map<String, Object> merged = new LinkedHashMap<>(base);
        merged.putAll(extra);
        return merged;
    }
}
