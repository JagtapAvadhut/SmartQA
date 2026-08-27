package com.smartqa.event;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.UUID;

/**
 * Thread-local + Reactor-context correlation IDs stamped onto SSE events.
 * Never emit the literal "undefined" when a valid ID exists in context.
 */
public final class RunCorrelation {

    public static final String CTX_PIPELINE = "pipelineRunId";
    public static final String CTX_GENERATION = "generationRunId";
    public static final String CTX_TEST_CASE = "testCaseId";

    private static final ThreadLocal<UUID> GENERATION = new ThreadLocal<>();
    private static final ThreadLocal<UUID> PIPELINE = new ThreadLocal<>();
    private static final ThreadLocal<UUID> TEST_CASE = new ThreadLocal<>();

    private RunCorrelation() {
    }

    public static void set(UUID pipelineRunId, UUID generationRunId) {
        set(pipelineRunId, generationRunId, TEST_CASE.get());
    }

    public static void set(UUID pipelineRunId, UUID generationRunId, UUID testCaseId) {
        if (pipelineRunId == null) {
            PIPELINE.remove();
        } else {
            PIPELINE.set(pipelineRunId);
        }
        if (generationRunId == null) {
            GENERATION.remove();
        } else {
            GENERATION.set(generationRunId);
        }
        if (testCaseId == null) {
            TEST_CASE.remove();
        } else {
            TEST_CASE.set(testCaseId);
        }
    }

    public static void clear() {
        PIPELINE.remove();
        GENERATION.remove();
        TEST_CASE.remove();
    }

    public static UUID pipelineRunId() {
        return PIPELINE.get();
    }

    public static UUID generationRunId() {
        return GENERATION.get();
    }

    public static UUID testCaseId() {
        return TEST_CASE.get();
    }

    public static boolean isMissingId(Object value) {
        if (value == null) {
            return true;
        }
        String s = String.valueOf(value).trim();
        return s.isBlank()
                || "undefined".equalsIgnoreCase(s)
                || "null".equalsIgnoreCase(s);
    }

    public static Context writeContext(ContextView current, UUID pipelineRunId, UUID generationRunId, UUID testCaseId) {
        Context ctx = Context.empty();
        if (current != null) {
            ctx = ctx.putAll(current);
        }
        if (pipelineRunId != null) {
            ctx = ctx.put(CTX_PIPELINE, pipelineRunId);
        }
        if (generationRunId != null) {
            ctx = ctx.put(CTX_GENERATION, generationRunId);
        }
        if (testCaseId != null) {
            ctx = ctx.put(CTX_TEST_CASE, testCaseId);
        }
        return ctx;
    }

    public static void applyContext(ContextView ctx) {
        if (ctx == null) {
            return;
        }
        UUID pipeline = ctx.getOrDefault(CTX_PIPELINE, pipelineRunId());
        UUID generation = ctx.getOrDefault(CTX_GENERATION, generationRunId());
        UUID testCase = ctx.getOrDefault(CTX_TEST_CASE, testCaseId());
        set(pipeline, generation, testCase);
    }
}
