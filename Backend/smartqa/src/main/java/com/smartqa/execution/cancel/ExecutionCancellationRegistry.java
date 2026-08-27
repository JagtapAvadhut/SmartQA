package com.smartqa.execution.cancel;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active cancellation tokens keyed by executionRunId.
 */
@Component
public class ExecutionCancellationRegistry {

    private final ConcurrentHashMap<UUID, CancellationToken> tokens = new ConcurrentHashMap<>();

    public CancellationToken register(UUID executionRunId) {
        CancellationToken token = new CancellationToken();
        tokens.put(executionRunId, token);
        return token;
    }

    public CancellationToken get(UUID executionRunId) {
        return tokens.get(executionRunId);
    }

    public void requestStop(UUID executionRunId) {
        CancellationToken token = tokens.get(executionRunId);
        if (token != null) {
            token.requestStop();
        }
    }

    public boolean isStopRequested(UUID executionRunId) {
        CancellationToken token = tokens.get(executionRunId);
        return token != null && token.isStopRequested();
    }

    public void unregister(UUID executionRunId) {
        tokens.remove(executionRunId);
    }

    public boolean isRegistered(UUID executionRunId) {
        return tokens.containsKey(executionRunId);
    }
}
