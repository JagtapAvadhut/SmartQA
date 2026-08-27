package com.smartqa.pipeline;

import com.smartqa.workspace.WorkspaceAnalyzeRequest;

public record PipelineStartRequest(
        String applicationUrl,
        String instructions,
        java.util.UUID projectId,
        java.util.UUID testCaseId,
        java.util.List<WorkspaceAnalyzeRequest.StructuredStepDto> structuredSteps,
        String browserMode,
        Boolean headless,
        Boolean skipExecution,
        Integer maxAttempts
) {
}
