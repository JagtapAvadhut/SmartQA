package com.smartqa.execution.event;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bounded in-memory store for structured execution events.
 * Events are keyed by executionRunId (or traceId for generation events).
 */
@Component
public class ExecutionEventStore {

    private static final int MAX_EVENTS_PER_RUN = 500;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ExecutionEvent>> store = new ConcurrentHashMap<>();

    public void add(String runKey, ExecutionEvent event) {
        CopyOnWriteArrayList<ExecutionEvent> events = store.computeIfAbsent(runKey, k -> new CopyOnWriteArrayList<>());
        events.add(event);
        if (events.size() > MAX_EVENTS_PER_RUN) {
            events.remove(0);
        }
    }

    public void add(UUID executionRunId, ExecutionEvent event) {
        add(executionRunId.toString(), event);
    }

    public List<ExecutionEvent> get(String runKey) {
        CopyOnWriteArrayList<ExecutionEvent> events = store.get(runKey);
        return events == null ? Collections.emptyList() : Collections.unmodifiableList(events);
    }

    public List<ExecutionEvent> get(UUID executionRunId) {
        return get(executionRunId.toString());
    }

    public List<ExecutionEvent> getByStep(UUID executionRunId, int stepNumber) {
        List<ExecutionEvent> all = get(executionRunId);
        List<ExecutionEvent> filtered = new ArrayList<>();
        for (ExecutionEvent event : all) {
            if (event.stepNumber() == stepNumber) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    public void remove(String runKey) {
        store.remove(runKey);
    }

    public void remove(UUID executionRunId) {
        store.remove(executionRunId.toString());
    }

    public int size(String runKey) {
        CopyOnWriteArrayList<ExecutionEvent> events = store.get(runKey);
        return events == null ? 0 : events.size();
    }
}
