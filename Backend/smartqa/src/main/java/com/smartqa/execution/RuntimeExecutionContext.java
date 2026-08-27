package com.smartqa.execution;

import java.util.UUID;

/**
 * Thread-bound correlation for the live browser worker. Cleared in finally.
 */
public final class RuntimeExecutionContext {

    private static final ThreadLocal<UUID> TEST_CASE_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> EXECUTION_RUN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> STEP_ID = new ThreadLocal<>();

    private RuntimeExecutionContext() {
    }

    public static void bind(UUID testCaseId, UUID executionRunId, String stepId) {
        TEST_CASE_ID.set(testCaseId);
        EXECUTION_RUN_ID.set(executionRunId);
        STEP_ID.set(stepId);
    }

    public static void clear() {
        TEST_CASE_ID.remove();
        EXECUTION_RUN_ID.remove();
        STEP_ID.remove();
    }

    public static UUID testCaseId() {
        return TEST_CASE_ID.get();
    }

    public static UUID executionRunId() {
        return EXECUTION_RUN_ID.get();
    }

    public static String stepId() {
        return STEP_ID.get();
    }
}
