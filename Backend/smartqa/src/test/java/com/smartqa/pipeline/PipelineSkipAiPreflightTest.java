package com.smartqa.pipeline;

import com.smartqa.workspace.WorkspaceAnalyzeRequest.StructuredStepDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineSkipAiPreflightTest {

    @Test
    void structuredStepsSkipAiPreflight() {
        PipelineStartRequest request = new PipelineStartRequest(
                "https://example.com",
                "login",
                null,
                null,
                List.of(new StructuredStepDto("1", "click", "Login", null, null, "AUTO", null)),
                "headed",
                false,
                false,
                1
        );
        assertTrue(PipelineService.skipAiPreflight(request));
    }

    @Test
    void paragraphInstructionsDoNotSkipAiPreflight() {
        PipelineStartRequest request = new PipelineStartRequest(
                "https://example.com",
                "Login as Admin",
                null,
                null,
                null,
                "headed",
                false,
                false,
                1
        );
        assertFalse(PipelineService.skipAiPreflight(request));
        assertFalse(PipelineService.skipAiPreflight(null));
    }
}
