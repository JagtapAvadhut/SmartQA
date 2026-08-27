package com.smartqa.clarification;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeClarificationServiceTest {

    @Test
    void resolveCompletesAwaitWithoutGuessing() throws Exception {
        RuntimeClarificationService service = new RuntimeClarificationService(null);
        UUID testCaseId = UUID.randomUUID();
        RuntimeClarificationService.RuntimeClarification paused = service.pause(
                testCaseId,
                UUID.randomUUID(),
                "step-save",
                "Save",
                RuntimeClarificationService.TARGET_AMBIGUOUS,
                List.of(
                        Map.of("candidateId", "c1", "label", "Save dialog"),
                        Map.of("candidateId", "c2", "label", "Save toolbar")
                )
        );
        CompletableFuture<RuntimeClarificationService.RuntimeClarification> waiter =
                CompletableFuture.supplyAsync(() -> service.await(paused.id(), Duration.ofSeconds(2)));
        Thread.sleep(50);
        service.resolve(paused.id(), "c2");
        RuntimeClarificationService.RuntimeClarification resolved = waiter.get();
        assertNotNull(resolved);
        assertEquals("RESOLVED", resolved.status());
        assertEquals("c2", resolved.selectedCandidateId());
    }
}
