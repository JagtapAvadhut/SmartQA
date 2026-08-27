package com.smartqa.pipeline;

import com.smartqa.intent.ClarificationQuestion;
import com.smartqa.testcase.TestCaseResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PipelineRunResponse(
        UUID id,
        String status,
        String stage,
        String userStageLabel,
        UUID projectId,
        UUID testCaseId,
        UUID generationRunId,
        UUID validationRunId,
        UUID executionRunId,
        String applicationUrl,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorMessage,
        String finalSummary,
        FailureDiagnosis diagnosis,
        int attempt,
        int maxAttempts,
        List<String> userProgress,
        Map<String, Object> details,
        TestCaseResponse testCase,
        List<ClarificationQuestion> clarifications
) {
    public static PipelineRunResponse from(PipelineRun run) {
        return from(run, null, List.of());
    }

    public static PipelineRunResponse from(PipelineRun run, TestCaseResponse testCase) {
        return from(run, testCase, List.of());
    }

    public static PipelineRunResponse from(
            PipelineRun run,
            TestCaseResponse testCase,
            List<ClarificationQuestion> clarifications) {
        return new PipelineRunResponse(
                run.id(),
                run.status(),
                run.stage() == null ? null : run.stage().name(),
                run.userStageLabel(),
                run.projectId(),
                run.testCaseId(),
                run.generationRunId(),
                run.validationRunId(),
                run.executionRunId(),
                run.applicationUrl(),
                run.startedAt(),
                run.finishedAt(),
                run.durationMs(),
                run.errorMessage(),
                run.finalSummary(),
                run.diagnosis(),
                run.attempt(),
                run.maxAttempts(),
                run.userProgress(),
                run.details(),
                testCase,
                clarifications == null ? List.of() : clarifications
        );
    }
}
