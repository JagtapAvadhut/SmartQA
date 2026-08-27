package com.smartqa.event;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionEventTest {

    @Test
    void mapsPipelineFailureToErrorLevel() {
        UUID pipelineId = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();
        ProgressEvent source = ProgressEvent.generation(
                "PIPELINE_FAILED",
                "Login did not leave the login page",
                testCaseId,
                Map.of("pipelineRunId", pipelineId.toString(), "errorCode", "LOGIN_STATE_FAILURE"));
        ExecutionEvent event = ExecutionEvent.fromProgress(source, pipelineId);
        assertEquals("ERROR", event.level());
        assertEquals("PIPELINE_FAILED", event.eventType());
        assertEquals(pipelineId, event.pipelineRunId());
        assertEquals(testCaseId, event.testCaseId());
    }
}
