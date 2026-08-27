package com.smartqa.generation;

import java.time.Instant;
import java.util.UUID;

public record GenerationRun(
        UUID id,
        UUID testCaseId,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorMessage,
        String failedStep,
        String result
) {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String STOPPED = "STOPPED";
    public static final String WAITING_FOR_CLARIFICATION = "WAITING_FOR_CLARIFICATION";

    public static GenerationRun start(UUID testCaseId) {
        return new GenerationRun(UUID.randomUUID(), testCaseId, RUNNING, Instant.now(), null, null, null, null, null);
    }

    public GenerationRun complete() {
        return complete("SUCCESS");
    }

    public GenerationRun complete(String result) {
        Instant now = Instant.now();
        return new GenerationRun(id, testCaseId, COMPLETED, startedAt, now, duration(now), null, null, result);
    }

    public GenerationRun fail(String error) {
        return fail(error, null);
    }

    public GenerationRun fail(String error, String failedStep) {
        Instant now = Instant.now();
        return new GenerationRun(id, testCaseId, FAILED, startedAt, now, duration(now), error, failedStep, "FAILED");
    }

    public GenerationRun stop(String reason) {
        Instant now = Instant.now();
        return new GenerationRun(id, testCaseId, STOPPED, startedAt, now, duration(now), reason, null, "STOPPED");
    }

    public GenerationRun waitingForClarification() {
        return new GenerationRun(id, testCaseId, WAITING_FOR_CLARIFICATION, startedAt, null, null, null, null, null);
    }

    public boolean isTerminal() {
        return COMPLETED.equals(status) || FAILED.equals(status) || STOPPED.equals(status);
    }

    private Long duration(Instant finishedAt) {
        return startedAt == null ? null : finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
