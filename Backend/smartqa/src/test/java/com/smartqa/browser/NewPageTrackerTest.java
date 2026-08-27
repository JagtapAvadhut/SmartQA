package com.smartqa.browser;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewPageTrackerTest {

    @Test
    void captureWithNullPageIsSafe() {
        NewPageTracker.Capture capture = NewPageTracker.capture(null);
        assertTrue(capture.pagesBefore().isEmpty());
        NewPageTracker.Result result = NewPageTracker.resolveAfterAction(null, capture, new AtomicReference<>());
        assertFalse(result.opened());
    }
}
