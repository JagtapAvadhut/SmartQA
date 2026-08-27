package com.smartqa.execution;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExecutionRunResponse(
        UUID id,
        UUID testCaseId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        Integer exitCode,
        String stdout,
        String stderr,
        String errorMessage,
        String scenarioResults,
        String healingEvents,
        LocalDateTime createdAt
) {
    public static ExecutionRunResponse from(ExecutionRun run) {
        return new ExecutionRunResponse(
                run.getId(),
                run.getTestCaseId(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getExitCode(),
                run.getStdout(),
                run.getStderr(),
                run.getErrorMessage(),
                run.getScenarioResults(),
                run.getHealingEvents(),
                run.getCreatedAt()
        );
    }
}
