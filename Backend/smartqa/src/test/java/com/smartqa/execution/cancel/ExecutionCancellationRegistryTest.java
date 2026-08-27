package com.smartqa.execution.cancel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionCancellationRegistryTest {

    @Test
    void registerAndGet() {
        ExecutionCancellationRegistry registry = new ExecutionCancellationRegistry();
        UUID id = UUID.randomUUID();
        CancellationToken token = registry.register(id);
        assertNotNull(token);
        assertSame(token, registry.get(id));
        assertTrue(registry.isRegistered(id));
    }

    @Test
    void unregister() {
        ExecutionCancellationRegistry registry = new ExecutionCancellationRegistry();
        UUID id = UUID.randomUUID();
        registry.register(id);
        registry.unregister(id);
        assertNull(registry.get(id));
        assertFalse(registry.isRegistered(id));
    }

    @Test
    void requestStop() {
        ExecutionCancellationRegistry registry = new ExecutionCancellationRegistry();
        UUID id = UUID.randomUUID();
        registry.register(id);
        assertFalse(registry.isStopRequested(id));
        registry.requestStop(id);
        assertTrue(registry.isStopRequested(id));
    }

    @Test
    void requestStopOnUnknownIdDoesNotThrow() {
        ExecutionCancellationRegistry registry = new ExecutionCancellationRegistry();
        assertDoesNotThrow(() -> registry.requestStop(UUID.randomUUID()));
    }

    @Test
    void isStopRequestedOnUnknownReturnsFalse() {
        ExecutionCancellationRegistry registry = new ExecutionCancellationRegistry();
        assertFalse(registry.isStopRequested(UUID.randomUUID()));
    }

    @Test
    void sseDisconnectUnregisterDoesNotRequestStop() {
        ExecutionCancellationRegistry registry = new ExecutionCancellationRegistry();
        UUID id = UUID.randomUUID();
        CancellationToken token = registry.register(id);
        registry.unregister(id);
        assertFalse(token.isStopRequested());
        assertFalse(registry.isStopRequested(id));
    }

    @Test
    void concurrentRunsDoNotStopEachOther() {
        ExecutionCancellationRegistry registry = new ExecutionCancellationRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CancellationToken firstToken = registry.register(first);
        CancellationToken secondToken = registry.register(second);
        registry.requestStop(first);
        assertTrue(firstToken.isStopRequested());
        assertFalse(secondToken.isStopRequested());
        assertTrue(PlaywrightAlive.stillRegistered(registry, second));
    }

    private static final class PlaywrightAlive {
        static boolean stillRegistered(ExecutionCancellationRegistry registry, UUID id) {
            return registry.isRegistered(id) && !registry.isStopRequested(id);
        }
    }
}
