package com.smartqa.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressEventHubTest {

    @Test
    void assignsMonotonicEventIdsPerChannel() throws Exception {
        ProgressEventHub hub = new ProgressEventHub();
        String channel = ProgressEventHub.generationChannel(UUID.randomUUID());

        hub.emit(channel, ProgressEvent.generation("STEP_STARTED", "Step 1", UUID.randomUUID()));
        hub.emit(channel, ProgressEvent.generation("STEP_COMPLETED", "Step 1 done", UUID.randomUUID()));

        CopyOnWriteArrayList<Long> ids = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        hub.stream(channel).take(2).subscribe(event -> {
            ids.add(event.eventId());
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, ids.size());
        assertTrue(ids.get(0) < ids.get(1));
        assertEquals(2L, hub.latestEventId(channel));
    }

    @Test
    void replayBufferAllowsLateSubscriberRecovery() throws Exception {
        ProgressEventHub hub = new ProgressEventHub();
        UUID testCaseId = UUID.randomUUID();
        String channel = ProgressEventHub.generationChannel(testCaseId);

        hub.emit(channel, ProgressEvent.generation("BROWSER_STARTED", "Browser started", testCaseId));
        hub.emit(channel, ProgressEvent.generation("DOM_FETCHED", "Inspected elements", testCaseId));

        CountDownLatch latch = new CountDownLatch(2);
        hub.stream(channel).take(2).subscribe(event -> latch.countDown());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void lastEventIdSkipsAlreadySeenEvents() throws Exception {
        ProgressEventHub hub = new ProgressEventHub();
        UUID testCaseId = UUID.randomUUID();
        String channel = ProgressEventHub.generationChannel(testCaseId);
        hub.emit(channel, ProgressEvent.generation("STEP_STARTED", "Step 1", testCaseId));
        hub.emit(channel, ProgressEvent.generation("STEP_COMPLETED", "Step 1 done", testCaseId));
        hub.emit(channel, ProgressEvent.generation("GENERATION_COMPLETE", "Done", testCaseId));

        CopyOnWriteArrayList<String> types = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        hub.stream(channel, 1L).take(2).subscribe(event -> {
            types.add(event.type());
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, types.size());
        assertEquals("STEP_COMPLETED", types.get(0));
        assertEquals("GENERATION_COMPLETE", types.get(1));
    }

    @Test
    void mirrorsAiEventsOntoPipelineChannel() throws Exception {
        ProgressEventHub hub = new ProgressEventHub();
        UUID pipelineId = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();
        RunCorrelation.set(pipelineId, null, testCaseId);
        try {
            String generation = ProgressEventHub.generationChannel(testCaseId);
            String pipeline = ProgressEventHub.pipelineChannel(pipelineId);
            hub.emit(generation, ProgressEvent.generation("AI_FALLBACK_STARTED", "Switching AI provider", testCaseId));

            CopyOnWriteArrayList<String> types = new CopyOnWriteArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);
            hub.stream(pipeline).take(1).subscribe(event -> {
                types.add(event.type());
                latch.countDown();
            });
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals("AI_FALLBACK_STARTED", types.get(0));
        } finally {
            RunCorrelation.clear();
        }
    }

    @Test
    void doesNotMirrorNonAiEventsToPipeline() throws Exception {
        ProgressEventHub hub = new ProgressEventHub();
        UUID pipelineId = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();
        RunCorrelation.set(pipelineId, null, testCaseId);
        try {
            hub.emit(ProgressEventHub.generationChannel(testCaseId),
                    ProgressEvent.generation("STEP_STARTED", "Step 1", testCaseId));
            CountDownLatch latch = new CountDownLatch(1);
            hub.stream(ProgressEventHub.pipelineChannel(pipelineId)).take(1).subscribe(event -> latch.countDown());
            assertFalse(latch.await(300, TimeUnit.MILLISECONDS), "non-AI events must not appear on the pipeline stream");
        } finally {
            RunCorrelation.clear();
        }
    }
}
