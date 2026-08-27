package com.smartqa.workspace;

import com.smartqa.intent.IntentFilter;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record WorkspaceAnalyzeRequest(
        @NotBlank(message = "Application URL is required") String applicationUrl,
        String instructions,
        UUID projectId,
        UUID testCaseId,
        List<StructuredStepDto> structuredSteps
) {
    public WorkspaceAnalyzeRequest(String applicationUrl, String instructions, UUID projectId, UUID testCaseId) {
        this(applicationUrl, instructions, projectId, testCaseId, null);
    }

    public record StructuredStepDto(
            String id,
            String action,
            String target,
            String value,
            String assertion,
            String location,
            IntentFilter filter
    ) {
    }
}
