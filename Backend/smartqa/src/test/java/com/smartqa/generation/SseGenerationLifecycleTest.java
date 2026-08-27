package com.smartqa.generation;

import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves SSE disconnect does not imply execution termination: events continue on the hub
 * while subscribers disconnect and reconnect independently.
 */
class SseGenerationLifecycleTest {

    @Test
    void browserEventsContinueAfterSubscriberDisconnects() throws Exception {
        ProgressEventHub hub = new ProgressEventHub();
        UUID testCaseId = UUID.randomUUID();
        String channel = ProgressEventHub.generationChannel(testCaseId);

        CopyOnWriteArrayList<ProgressEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch firstBatch = new CountDownLatch(2);
        var subscription = hub.stream(channel).subscribe(event -> {
            received.add(event);
            firstBatch.countDown();
        });

        hub.emit(channel, ProgressEvent.generation("BROWSER_STARTED", "Browser started", testCaseId));
        hub.emit(channel, ProgressEvent.generation("STEP_STARTED", "Step 1", testCaseId));
        assertTrue(firstBatch.await(2, TimeUnit.SECONDS));

        subscription.dispose();

        hub.emit(channel, ProgressEvent.generation("STEP_COMPLETED", "Step 1 done", testCaseId));
        hub.emit(channel, ProgressEvent.generation("GENERATION_COMPLETE", "Done", testCaseId));

        CountDownLatch recovered = new CountDownLatch(2);
        CopyOnWriteArrayList<Long> replayIds = new CopyOnWriteArrayList<>();
        hub.stream(channel)
                .filter(event -> "STEP_COMPLETED".equals(event.type()) || "GENERATION_COMPLETE".equals(event.type()))
                .take(2)
                .subscribe(event -> {
                    replayIds.add(event.eventId());
                    recovered.countDown();
                });

        assertTrue(recovered.await(2, TimeUnit.SECONDS));
        assertTrue(replayIds.stream().allMatch(id -> id != null && id > 0));
    }

    @Test
    void generationRunReachesTerminalStateIndependentlyOfSse() throws Exception {
        GenerationRunRegistry registry = new GenerationRunRegistry();
        UUID testCaseId = UUID.randomUUID();
        GenerationRun run = GenerationRun.start(testCaseId);
        registry.register(run);

        CountDownLatch finished = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            registry.update(run.complete("SUCCESS"));
            finished.countDown();
        });
        worker.start();
        assertTrue(finished.await(2, TimeUnit.SECONDS));

        GenerationRun terminal = registry.get(run.id());
        assertFalse(registry.isRunning(testCaseId));
        assertTrue(terminal.isTerminal());
        assertTrue(GenerationRun.COMPLETED.equals(terminal.status()));
    }
}
