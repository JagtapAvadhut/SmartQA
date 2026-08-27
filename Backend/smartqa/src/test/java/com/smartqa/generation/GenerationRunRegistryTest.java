package com.smartqa.generation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationRunRegistryTest {

    @Test
    void runTransitionsToCompletedWithResult() {
        GenerationRunRegistry registry = new GenerationRunRegistry();
        UUID testCaseId = UUID.randomUUID();
        GenerationRun run = GenerationRun.start(testCaseId);
        registry.register(run);

        assertTrue(registry.isRunning(testCaseId));

        GenerationRun completed = run.complete("SUCCESS");
        registry.update(completed);

        assertFalse(registry.isRunning(testCaseId));
        GenerationRun stored = registry.get(run.id());
        assertEquals(GenerationRun.COMPLETED, stored.status());
        assertEquals("SUCCESS", stored.result());
        assertNotNull(stored.finishedAt());
        assertNotNull(stored.durationMs());
        assertEquals(run.id(), registry.getByTestCase(testCaseId).id());
        assertEquals(GenerationRun.COMPLETED, registry.getByTestCase(testCaseId).status());
    }

    @Test
    void runTransitionsToFailedWithError() {
        GenerationRunRegistry registry = new GenerationRunRegistry();
        UUID testCaseId = UUID.randomUUID();
        GenerationRun run = GenerationRun.start(testCaseId);
        registry.register(run);

        registry.update(run.fail("Browser execution failed", "step-3"));

        GenerationRun stored = registry.get(run.id());
        assertEquals(GenerationRun.FAILED, stored.status());
        assertEquals("Browser execution failed", stored.errorMessage());
        assertEquals("step-3", stored.failedStep());
        assertFalse(registry.isRunning(testCaseId));
    }

    @Test
    void ensureTerminalMarksStaleRunAsFailed() {
        GenerationRunRegistry registry = new GenerationRunRegistry();
        UUID testCaseId = UUID.randomUUID();
        GenerationRun run = new GenerationRun(
                UUID.randomUUID(),
                testCaseId,
                GenerationRun.RUNNING,
                Instant.now().minusSeconds(3600),
                null,
                null,
                null,
                null,
                null
        );
        registry.register(run);

        assertFalse(registry.isRunning(testCaseId));
        GenerationRun stored = registry.get(run.id());
        assertEquals(GenerationRun.FAILED, stored.status());
        assertNotNull(stored.errorMessage());
    }

    @Test
    void ensureTerminalGuaranteesTerminalStateAfterCrash() {
        GenerationRunRegistry registry = new GenerationRunRegistry();
        UUID testCaseId = UUID.randomUUID();
        GenerationRun run = GenerationRun.start(testCaseId);
        registry.register(run);

        GenerationRun forced = registry.ensureTerminal(run.id(), "Execution thread crashed");
        assertNotNull(forced);
        assertEquals(GenerationRun.FAILED, forced.status());
        assertFalse(registry.isRunning(testCaseId));
    }
}
