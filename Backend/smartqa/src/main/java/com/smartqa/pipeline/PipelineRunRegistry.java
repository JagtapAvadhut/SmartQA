package com.smartqa.pipeline;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PipelineRunRegistry {

    private final ConcurrentHashMap<UUID, PipelineRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> byTestCase = new ConcurrentHashMap<>();

    public void register(PipelineRun run) {
        runs.put(run.id(), run);
        if (run.testCaseId() != null) {
            byTestCase.put(run.testCaseId(), run.id());
        }
    }

    public void bindTestCase(PipelineRun run, UUID testCaseId) {
        if (testCaseId != null) {
            byTestCase.put(testCaseId, run.id());
        }
    }

    public PipelineRun get(UUID id) {
        return runs.get(id);
    }

    public PipelineRun getByTestCase(UUID testCaseId) {
        UUID id = byTestCase.get(testCaseId);
        return id == null ? null : runs.get(id);
    }

    public boolean isRunning(UUID testCaseId) {
        PipelineRun run = getByTestCase(testCaseId);
        return run != null && !run.isTerminal();
    }
}
