package com.smartqa.execution.cancel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CancellationTokenTest {

    @Test
    void initiallyNotStopped() {
        CancellationToken token = new CancellationToken();
        assertFalse(token.isStopRequested());
    }

    @Test
    void requestStopChangesState() {
        CancellationToken token = new CancellationToken();
        token.requestStop();
        assertTrue(token.isStopRequested());
    }

    @Test
    void throwIfStoppedThrowsWhenRequested() {
        CancellationToken token = new CancellationToken();
        token.requestStop();
        assertThrows(ExecutionCancelledException.class, token::throwIfStopped);
    }

    @Test
    void throwIfStoppedDoesNothingWhenNotRequested() {
        CancellationToken token = new CancellationToken();
        assertDoesNotThrow(token::throwIfStopped);
    }

    @Test
    void multipleRequestStopCallsAreIdempotent() {
        CancellationToken token = new CancellationToken();
        token.requestStop();
        token.requestStop();
        assertTrue(token.isStopRequested());
    }
}
