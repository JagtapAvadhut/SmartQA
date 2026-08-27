package com.smartqa.browser;

import com.smartqa.event.ProgressEvent;
import com.smartqa.execution.cancel.CancellationToken;

import java.util.function.Consumer;

public interface BrowserExecutionProvider {

    String id();

    LocatorMemoryDocument execute(ExecutionPlan plan, Consumer<ProgressEvent> progress);

    default LocatorMemoryDocument execute(ExecutionPlan plan, Consumer<ProgressEvent> progress, CancellationToken cancellationToken) {
        return execute(plan, progress);
    }

    default LocatorMemoryDocument execute(
            ExecutionPlan plan,
            Consumer<ProgressEvent> progress,
            CancellationToken cancellationToken,
            BrowserExecutionOptions options) {
        return execute(plan, progress, cancellationToken);
    }
}
