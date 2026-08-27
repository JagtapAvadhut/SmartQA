package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelinePassSemanticsTest {

    @Test
    void skipExecutionIsNotFullPipelinePass() {
        PipelineRun run = PipelineRun.start();
        run.bindWorkspace(UUID.randomUUID(), UUID.randomUUID(), "https://example.com");
        run.markValidatedNotExecuted("validated only");

        assertEquals(PipelineRun.STATUS_VALIDATED_NOT_EXECUTED, run.status());
        assertTrue(run.isTerminal());
        assertFalse(Boolean.TRUE.equals(run.details().get("pipelinePass")));
        assertEquals("SKIPPED", run.details().get("execution"));
        assertEquals("PASSED", run.details().get("validation"));
    }

    @Test
    void fullPassRequiresExecutionPassedDetail() {
        PipelineRun run = PipelineRun.start();
        run.markPass("full pass");

        assertEquals(PipelineRun.STATUS_PASS, run.status());
        assertTrue(Boolean.TRUE.equals(run.details().get("pipelinePass")));
        assertEquals("PASSED", run.details().get("execution"));
        assertEquals("PASSED", run.details().get("validation"));
    }

    @Test
    void assertionFailureMarksFailNotPass() {
        PipelineRun run = PipelineRun.start();
        FailureDiagnosis diagnosis = FailureDiagnosis.of(
                "ASSERTION_FAILURE",
                "ASSERTION",
                "ASSERTION",
                "ASSERTION_FAILURE",
                "Expected text missing",
                false,
                1,
                "Re-check assertion on live page");
        run.markFail("Assertion failed", diagnosis);

        assertEquals(PipelineRun.STATUS_FAIL, run.status());
        assertTrue(run.isTerminal());
        assertFalse(PipelineRun.STATUS_PASS.equals(run.status()));
    }

    @Test
    void abandonedAfterRestartIsTerminalAndNotPass() {
        PipelineRun run = PipelineRun.rehydrate(
                UUID.randomUUID(),
                PipelineRun.STATUS_RUNNING,
                "EXECUTE",
                "Running",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                "https://example.com",
                java.time.Instant.now().minusSeconds(60),
                null,
                null,
                null,
                null,
                1,
                3);
        run.markAbandoned("interrupted");

        assertEquals(PipelineRun.STATUS_ABANDONED, run.status());
        assertTrue(run.isTerminal());
        assertFalse(PipelineRun.STATUS_PASS.equals(run.status()));
    }

    @Test
    void failCannotTransitionToPass() {
        PipelineRun run = PipelineRun.start();
        run.markFail("failed", FailureDiagnosis.of(
                "FAIL", "ASSERTION", "ASSERTION", "ASSERTION_FAILURE", "x", false, 1, "retry"));
        assertThrows(IllegalStateException.class, () -> run.markPass("should not pass"));
        assertEquals(PipelineRun.STATUS_FAIL, run.status());
    }

    @Test
    void createdAtIsPresent() {
        PipelineRun run = PipelineRun.start();
        assertTrue(run.createdAt() != null);
        assertTrue(run.startedAt() != null);
    }
}
