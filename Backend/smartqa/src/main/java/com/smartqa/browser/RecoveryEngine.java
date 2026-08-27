package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.smartqa.browser.intelligence.PageStateWatcher;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Bounded recovery for actionability / overlay / stale-DOM failures.
 * Never retries infinitely. Never uses force=true.
 */
public final class RecoveryEngine {

    public static final int MAX_RETRIES = 3;

    public enum FailureCategory {
        ELEMENT_NOT_FOUND,
        AMBIGUOUS_ELEMENT,
        ELEMENT_NOT_ACTIONABLE,
        ELEMENT_COVERED,
        OVERLAY_PRESENT,
        STALE_DOM,
        WRONG_FRAME,
        DROPDOWN_NOT_OPEN,
        AUTOCOMPLETE_NOT_READY,
        FILTER_NOT_APPLIED,
        SEARCH_STATE_MISMATCH,
        LOCATION_STATE_MISMATCH,
        QUANTITY_STATE_MISMATCH,
        CART_STATE_MISMATCH,
        AI_FAILURE,
        STATE_NOT_CHANGED,
        ASSERTION_FAILED,
        PAGE_NOT_READY,
        UNKNOWN
    }

    public record Attempt(int attempt, FailureCategory category, String reason, boolean recovered) {
    }

    private RecoveryEngine() {
    }

    public static FailureCategory classify(Throwable error) {
        if (error == null) {
            return FailureCategory.UNKNOWN;
        }
        if (error instanceof com.smartqa.common.error.SmartQaException sqa) {
            ErrorCode code = sqa.errorCode();
            if (code == ErrorCode.ELEMENT_NOT_FOUND || code == ErrorCode.LOCATOR_NOT_FOUND
                    || code == ErrorCode.LOCATOR_INVALID || code == ErrorCode.LOCATOR_FAILURE) {
                return FailureCategory.ELEMENT_NOT_FOUND;
            }
            if (code == ErrorCode.AMBIGUOUS_ELEMENT) {
                return FailureCategory.AMBIGUOUS_ELEMENT;
            }
            if (code == ErrorCode.ACTIONABILITY_FAILURE) {
                String msg = sqa.getMessage() == null ? "" : sqa.getMessage().toLowerCase(Locale.ROOT);
                if (msg.contains("cover") || msg.contains("overlay")) {
                    return FailureCategory.ELEMENT_COVERED;
                }
                return FailureCategory.ELEMENT_NOT_ACTIONABLE;
            }
            if (code == ErrorCode.ASSERTION_FAILED) {
                return FailureCategory.ASSERTION_FAILED;
            }
            if (code == ErrorCode.FILTER_APPLICATION_FAILURE || code == ErrorCode.FILTER_VALIDATION_FAILURE) {
                return FailureCategory.FILTER_NOT_APPLIED;
            }
            if (code == ErrorCode.SEARCH_STATE_MISMATCH) {
                return FailureCategory.SEARCH_STATE_MISMATCH;
            }
            if (code == ErrorCode.LOCATION_STATE_MISMATCH) {
                return FailureCategory.LOCATION_STATE_MISMATCH;
            }
            if (code == ErrorCode.QUANTITY_STATE_MISMATCH) {
                return FailureCategory.QUANTITY_STATE_MISMATCH;
            }
            if (code == ErrorCode.CART_STATE_MISMATCH) {
                return FailureCategory.CART_STATE_MISMATCH;
            }
            if (code == ErrorCode.STALE_ELEMENT) {
                return FailureCategory.STALE_DOM;
            }
            if (code == ErrorCode.AI_TIMEOUT || code == ErrorCode.AI_PROVIDER_ERROR
                    || code == ErrorCode.AI_RESPONSE_INVALID || code == ErrorCode.AI_RATE_LIMITED
                    || code == ErrorCode.AI_UNAVAILABLE || code == ErrorCode.AI_PROVIDERS_UNAVAILABLE) {
                return FailureCategory.AI_FAILURE;
            }
        }
        String msg = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (BlockingOverlayGuard.looksLikePointerIntercept(error) || msg.contains("intercepts pointer")) {
            return FailureCategory.ELEMENT_COVERED;
        }
        if (msg.contains("not visible") || msg.contains("not enabled") || msg.contains("not actionable")) {
            return FailureCategory.ELEMENT_NOT_ACTIONABLE;
        }
        if (msg.contains("execution context was destroyed") || msg.contains("frame was detached")
                || msg.contains("not attached to the DOM") || msg.contains("element is not attached")
                || msg.contains("target closed") || msg.contains("because of a DOM update")
                || msg.contains("stale element")) {
            return FailureCategory.STALE_DOM;
        }
        if (msg.contains("timeout") && msg.contains("waiting for")) {
            return FailureCategory.PAGE_NOT_READY;
        }
        return FailureCategory.UNKNOWN;
    }

    public static boolean tryRecover(Page page, FailureCategory category, int attempt) {
        TraceLogger.info("RECOVERY", "RECOVERY_STARTED", "Starting recovery", TraceMeta.of(
                "category", category.name(),
                "attempt", attempt,
                "maxRetries", MAX_RETRIES
        ));
        boolean recovered = switch (category) {
            case ELEMENT_COVERED, OVERLAY_PRESENT, ELEMENT_NOT_ACTIONABLE ->
                    BlockingOverlayGuard.dismissIfBlocking(page);
            case STALE_DOM, PAGE_NOT_READY -> {
                SafeClick.settle(page);
                yield true;
            }
            case DROPDOWN_NOT_OPEN, AUTOCOMPLETE_NOT_READY -> {
                BlockingOverlayGuard.dismissIfBlocking(page);
                PageStateWatcher.waitForSubtreeSettle(page, "body", 80, 400);
                yield true;
            }
            case SEARCH_STATE_MISMATCH, LOCATION_STATE_MISMATCH -> {
                BlockingOverlayGuard.dismissIfBlocking(page);
                SafeClick.settle(page);
                yield true;
            }
            case QUANTITY_STATE_MISMATCH, CART_STATE_MISMATCH, AI_FAILURE -> false;
            default -> false;
        };
        TraceLogger.info("RECOVERY", "RECOVERY_COMPLETED", "Recovery attempt finished", TraceMeta.of(
                "category", category.name(),
                "attempt", attempt,
                "recovered", recovered
        ));
        return recovered;
    }

    public static <T> T withRetry(Page page, Supplier<T> action) {
        RecoveryCircuit circuit = RecoveryCircuit.defaults();
        RuntimeException last = null;
        String lastFingerprint = fingerprint(page);
        for (int attempt = 1; attempt <= circuit.maxRetries(); attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                last = ex;
                FailureCategory category = classify(ex);
                String fingerprint = fingerprint(page);
                if (fingerprint.equals(lastFingerprint) && !circuit.noteSameState()) {
                    throw circuit.exhaustedException("same page state after " + category.name());
                }
                lastFingerprint = fingerprint;
                boolean recorded = circuit.tryRetry();
                if (!recorded || !tryRecover(page, category, attempt)) {
                    throw circuit.exhaustedException(truncate(ex.getMessage(), 160));
                }
                TraceLogger.warn("RECOVERY", "RECOVERY_RETRY", "Retrying after recovery", TraceMeta.of(
                        "attempt", attempt,
                        "category", category.name(),
                        "retryCount", circuit.retryCount(),
                        "sameStateCount", circuit.sameStateCount(),
                        "reason", truncate(ex.getMessage(), 160)
                ));
            }
        }
        throw last;
    }

    static String fingerprint(Page page) {
        if (page == null) {
            return "";
        }
        try {
            return page.url() + "|" + page.title();
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
