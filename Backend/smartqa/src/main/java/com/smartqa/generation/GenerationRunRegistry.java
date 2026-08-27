package com.smartqa.generation;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GenerationRunRegistry {

    private static final Duration STALE_RUN_TIMEOUT = Duration.ofMinutes(45);

    private final ConcurrentHashMap<UUID, GenerationRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> activeByTestCase = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> lastByTestCase = new ConcurrentHashMap<>();

    public void register(GenerationRun run) {
        runs.put(run.id(), run);
        activeByTestCase.put(run.testCaseId(), run.id());
        lastByTestCase.put(run.testCaseId(), run.id());
    }

    public void update(GenerationRun run) {
        runs.put(run.id(), run);
        lastByTestCase.put(run.testCaseId(), run.id());
        if (run.isTerminal()) {
            UUID active = activeByTestCase.get(run.testCaseId());
            if (active != null && active.equals(run.id())) {
                activeByTestCase.remove(run.testCaseId(), run.id());
            }
        }
    }

    public GenerationRun get(UUID runId) {
        return runs.get(runId);
    }

    public GenerationRun getByTestCase(UUID testCaseId) {
        UUID runId = activeByTestCase.get(testCaseId);
        if (runId == null) {
            runId = lastByTestCase.get(testCaseId);
        }
        return runId == null ? null : runs.get(runId);
    }

    public boolean isRunning(UUID testCaseId) {
        GenerationRun run = getByTestCase(testCaseId);
        if (run == null) {
            return false;
        }
        if (run.isTerminal()) {
            return false;
        }
        if (isStale(run)) {
            update(run.fail("Generation run timed out after " + STALE_RUN_TIMEOUT.toMinutes() + " minutes"));
            return false;
        }
        return true;
    }

    public GenerationRun ensureTerminal(UUID runId, String reason) {
        GenerationRun current = get(runId);
        if (current == null) {
            return null;
        }
        if (current.isTerminal()) {
            return current;
        }
        GenerationRun failed = current.fail(reason == null ? "Generation did not reach terminal state" : reason);
        update(failed);
        return failed;
    }

    private boolean isStale(GenerationRun run) {
        if (run.startedAt() == null) {
            return false;
        }
        return Duration.between(run.startedAt(), Instant.now()).compareTo(STALE_RUN_TIMEOUT) > 0;
    }
}
