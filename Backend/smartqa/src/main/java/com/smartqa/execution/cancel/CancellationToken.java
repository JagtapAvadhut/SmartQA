package com.smartqa.execution.cancel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe cancellation token for cooperative stop.
 * Checked before every expensive operation in the execution pipeline.
 */
public class CancellationToken {

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public void requestStop() {
        stopRequested.set(true);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public void throwIfStopped() {
        if (stopRequested.get()) {
            throw new ExecutionCancelledException("Execution cancelled by user");
        }
    }
}
