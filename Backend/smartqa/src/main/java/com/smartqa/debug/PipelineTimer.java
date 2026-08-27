package com.smartqa.debug;

import com.smartqa.event.RunCorrelation;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Structured component timings on the existing trace path. Not a second telemetry engine.
 */
public final class PipelineTimer {

    private PipelineTimer() {
    }

    public static <T> T time(String component, Supplier<T> work) {
        return timed(component, "", work);
    }

    public static <T> T time(String component, String stepId, Supplier<T> work) {
        return timed(component, stepId, work);
    }

    public static void time(String component, Runnable work) {
        timed(component, "", () -> {
            work.run();
            return null;
        });
    }

    private static <T> T timed(String component, String stepId, Supplier<T> work) {
        long started = System.nanoTime();
        try {
            return work.get();
        } finally {
            record(component, stepId, (System.nanoTime() - started) / 1_000_000L);
        }
    }

    public static void record(String component, String stepId, long durationMs) {
        UUID pipeline = RunCorrelation.pipelineRunId();
        UUID generation = RunCorrelation.generationRunId();
        TraceLogger.info(component, "TIMING", component + " duration", durationMs, TraceMeta.of(
                "traceId", TraceContext.current(),
                "pipelineRunId", pipeline == null ? "" : pipeline.toString(),
                "executionRunId", generation == null ? "" : generation.toString(),
                "stepId", stepId == null ? "" : stepId,
                "component", component,
                "durationMs", durationMs
        ));
    }
}
