package com.smartqa.validation;

import java.time.LocalDateTime;
import java.util.UUID;

public record ValidationRunResponse(
        UUID id,
        UUID testCaseId,
        String status,
        String result,
        int attemptNumber,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        String stdout,
        String stderr,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static ValidationRunResponse from(ValidationRun run) {
        return new ValidationRunResponse(
                run.getId(), run.getTestCaseId(), run.getStatus(), run.getResult(),
                run.getAttemptNumber(), run.getStartedAt(), run.getFinishedAt(),
                run.getDurationMs(), run.getStdout(), run.getStderr(),
                run.getErrorMessage(), run.getCreatedAt()
        );
    }
}
