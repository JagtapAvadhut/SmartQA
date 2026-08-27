package com.smartqa.execution.cancel;

/**
 * Thrown when an execution is cooperatively cancelled via CancellationToken.
 * Must be handled as EXECUTION_STOPPED, not ERROR.
 */
public class ExecutionCancelledException extends RuntimeException {

    public ExecutionCancelledException(String message) {
        super(message);
    }
}
