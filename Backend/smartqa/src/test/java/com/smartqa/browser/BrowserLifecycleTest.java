package com.smartqa.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserLifecycleTest {

    @Test
    void closedTargetFailureIsDetectedFromMessageAndTypeName() {
        assertTrue(BrowserLifecycle.isClosedTargetFailure("Target page, context or browser has been closed"));
        assertTrue(BrowserLifecycle.isClosedTargetFailure(new RuntimeException("TargetClosedError: Target page, context or browser has been closed")));
        assertFalse(BrowserLifecycle.isClosedTargetFailure("net::ERR_NETWORK_CHANGED"));
    }

    @Test
    void warmupMustNotTreatClosedPageAsTransient() {
        assertFalse(PlaywrightBrowserLauncher.isTransientNavigationFailure(
                "Target page, context or browser has been closed"));
        assertFalse(PlaywrightBrowserLauncher.isTransientNavigationFailure("TargetClosedError"));
        assertTrue(PlaywrightBrowserLauncher.isTransientNavigationFailure("net::ERR_NETWORK_CHANGED"));
    }

    @Test
    void recoverableLaunchFailureIncludesExtensionAndClosed() {
        assertTrue(BrowserLifecycle.isRecoverableLaunchFailure(
                new IllegalStateException("Target page, context or browser has been closed")));
        assertTrue(BrowserLifecycle.isRecoverableLaunchFailure(
                new RuntimeException("Failed to load extension at persistent user-data-dir")));
        assertFalse(BrowserLifecycle.isRecoverableLaunchFailure(new IllegalArgumentException("unknown locator")));
    }

    @Test
    void closeReasonMapsCancelledAndClosedSeparately() {
        assertEquals(BrowserLifecycle.CLOSE_EXECUTION_COMPLETE, BrowserLifecycle.closeReasonFor(null));
        assertEquals(BrowserLifecycle.CLOSE_EXPLICIT_STOP, BrowserLifecycle.closeReasonFor(
                new com.smartqa.execution.cancel.ExecutionCancelledException("Execution cancelled by user")));
        assertEquals(BrowserLifecycle.CLOSE_TARGET_CLOSED, BrowserLifecycle.closeReasonFor(
                new RuntimeException("Target page, context or browser has been closed")));
        assertEquals(BrowserLifecycle.CLOSE_EXECUTION_FAILED, BrowserLifecycle.closeReasonFor(
                new RuntimeException("locator not found")));
    }

    @Test
    void classifyClosedResourcePrefersPageThenContextThenBrowser() {
        assertEquals(BrowserLifecycle.PAGE_CLOSED, BrowserLifecycle.classifyClosedResource(null, null, null));
    }
}
