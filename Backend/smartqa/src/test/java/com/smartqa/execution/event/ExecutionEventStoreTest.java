package com.smartqa.execution.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionEventStoreTest {

    @Test
    void addAndGet() {
        ExecutionEventStore store = new ExecutionEventStore();
        UUID runId = UUID.randomUUID();
        ExecutionEvent event = ExecutionEvent.builder()
                .eventType(EventType.BROWSER_STARTED)
                .component(EventComponent.BROWSER)
                .message("Browser started")
                .build();
        store.add(runId, event);
        assertEquals(1, store.get(runId).size());
        assertEquals(EventType.BROWSER_STARTED, store.get(runId).getFirst().eventType());
    }

    @Test
    void getByStep() {
        ExecutionEventStore store = new ExecutionEventStore();
        UUID runId = UUID.randomUUID();
        store.add(runId, ExecutionEvent.builder().stepNumber(1).eventType(EventType.ACTION_STARTED).build());
        store.add(runId, ExecutionEvent.builder().stepNumber(2).eventType(EventType.ACTION_STARTED).build());
        store.add(runId, ExecutionEvent.builder().stepNumber(1).eventType(EventType.ACTION_COMPLETED).build());
        assertEquals(2, store.getByStep(runId, 1).size());
        assertEquals(1, store.getByStep(runId, 2).size());
    }

    @Test
    void emptyForUnknown() {
        ExecutionEventStore store = new ExecutionEventStore();
        assertTrue(store.get(UUID.randomUUID()).isEmpty());
    }

    @Test
    void remove() {
        ExecutionEventStore store = new ExecutionEventStore();
        UUID runId = UUID.randomUUID();
        store.add(runId, ExecutionEvent.builder().eventType(EventType.BROWSER_STARTED).build());
        store.remove(runId);
        assertTrue(store.get(runId).isEmpty());
    }

    @Test
    void sizeTracking() {
        ExecutionEventStore store = new ExecutionEventStore();
        String key = "test-key";
        assertEquals(0, store.size(key));
        store.add(key, ExecutionEvent.builder().eventType(EventType.BROWSER_STARTED).build());
        assertEquals(1, store.size(key));
    }
}
