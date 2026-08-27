package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Single in-memory Gemini API key pool for the whole application.
 * Health-aware rotation, cooldown, and exhaustion live here — never in feature code.
 * Logs {@code keyIndex} only; the secret never appears in {@link #toString()}, snapshots, or traces.
 */
public final class GeminiKeyPool {

    public static final String STATE_UNKNOWN = "UNKNOWN";
    public static final String STATE_HEALTHY = "HEALTHY";
    public static final String STATE_COOLDOWN = "COOLDOWN";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_AVAILABLE_FOR_RETRY = "AVAILABLE_FOR_RETRY";

    private final SmartQaProperties properties;
    private final LongSupplier clock;
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, KeySlot> slots = new ConcurrentHashMap<>();

    public GeminiKeyPool() {
        this(new SmartQaProperties(), System::currentTimeMillis);
    }

    public GeminiKeyPool(SmartQaProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    public GeminiKeyPool(SmartQaProperties properties, LongSupplier clock) {
        this.properties = properties == null ? new SmartQaProperties() : properties;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public List<String> keys() {
        return properties.getAi().getGemini().resolvedApiKeys();
    }

    public boolean hasKeys() {
        return !keys().isEmpty();
    }

    public GeminiRotationPolicy policy() {
        return GeminiRotationPolicy.from(properties);
    }

    public int startIndex(int keyCount) {
        if (keyCount <= 0) {
            return 0;
        }
        return Math.floorMod(cursor.get(), keyCount);
    }

    public int nextHealthy(int keyCount, int startIndex, int attempt) {
        GeminiRotationPolicy policy = policy();
        if (keyCount <= 0 || attempt >= policy.maxAttempts(keyCount)) {
            return -1;
        }
        for (int i = 0; i < keyCount; i++) {
            int index = Math.floorMod(startIndex + i, keyCount);
            if (selectable(index)) {
                return index;
            }
        }
        return -1;
    }

    public boolean isCoolingDown(int index) {
        KeySlot slot = slots.get(index);
        if (slot == null || slot.cooldownUntilMs <= 0) {
            return false;
        }
        if (slot.cooldownUntilMs <= clock.getAsLong()) {
            slot.cooldownUntilMs = 0;
            if (STATE_COOLDOWN.equals(slot.state) || STATE_FAILED.equals(slot.state)) {
                slot.state = STATE_AVAILABLE_FOR_RETRY;
            }
            return false;
        }
        return true;
    }

    public void markSuccess(int index) {
        KeySlot slot = slot(index);
        slot.state = STATE_HEALTHY;
        slot.failureType = null;
        slot.cooldownUntilMs = 0;
        slot.lastSuccessAt = clock.getAsLong();
        List<String> keys = keys();
        if (keys.isEmpty()) {
            cursor.set(index + 1);
        } else {
            cursor.set(Math.floorMod(index + 1, keys.size()));
        }
    }

    public void markFailure(int index, Throwable error) {
        GeminiFailureKind kind = classify(error);
        GeminiRotationPolicy policy = policy();
        long now = clock.getAsLong();
        KeySlot slot = slot(index);
        slot.failureType = kind.name();
        slot.failureCount += 1;
        slot.lastFailureAt = now;
        if (kind == GeminiFailureKind.SCHEMA) {
            slot.state = STATE_HEALTHY;
            slot.cooldownUntilMs = 0;
            TraceLogger.warn("AI", "GEMINI_SCHEMA_FAILURE", "Structured output invalid; key stays healthy", TraceMeta.of(
                    "keyIndex", index + 1,
                    "failureType", kind.name(),
                    "reason", AiCalls.failureReason(error)
            ));
            return;
        }
        slot.state = kind == GeminiFailureKind.AUTH ? STATE_FAILED : STATE_COOLDOWN;
        slot.cooldownUntilMs = now + policy.cooldownFor(kind).toMillis();
        TraceLogger.warn("AI", "GEMINI_KEY_COOLDOWN", "Gemini key cooling down", TraceMeta.of(
                "keyIndex", index + 1,
                "keyCount", keys().size(),
                "failureType", kind.name(),
                "cooldownMs", policy.cooldownFor(kind).toMillis(),
                "reason", AiCalls.failureReason(error)
        ));
    }

    /**
     * Runs {@code invoker} with health-aware key rotation. HTTP stays in the caller.
     */
    public <T> Mono<T> execute(String operation, String model, Function<GeminiKeyLease, Mono<T>> invoker) {
        List<String> keys = keys();
        if (keys.isEmpty()) {
            TraceLogger.error("AI", "AI_REQUEST_FAILED", "GEMINI_API_KEY is not configured",
                    new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "GEMINI_API_KEY is not configured"),
                    null,
                    TraceMeta.of("provider", "gemini", "operation", operation, "reason", "NOT_CONFIGURED"));
            return Mono.error(new SmartQaException(
                    ErrorCode.AI_PROVIDER_ERROR,
                    "GEMINI_API_KEY is not configured"));
        }
        return invoke(operation, model, invoker, keys, startIndex(keys.size()), 0, null);
    }

    public GeminiPoolSnapshot snapshot() {
        List<String> keys = keys();
        List<GeminiPoolSnapshot.KeyView> views = new ArrayList<>();
        int cooldown = 0;
        int healthy = 0;
        for (int i = 0; i < keys.size(); i++) {
            isCoolingDown(i);
            KeySlot slot = slots.get(i);
            String state = slot == null ? STATE_UNKNOWN : slot.state;
            if (isCoolingDown(i)) {
                cooldown += 1;
                state = STATE_COOLDOWN;
            } else if (STATE_UNKNOWN.equals(state) || STATE_HEALTHY.equals(state) || STATE_AVAILABLE_FOR_RETRY.equals(state)) {
                healthy += 1;
            } else {
                healthy += 1;
            }
            views.add(new GeminiPoolSnapshot.KeyView(
                    i + 1,
                    state,
                    slot == null ? null : slot.failureType,
                    slot == null ? 0 : slot.failureCount,
                    slot == null ? null : slot.lastFailureAt,
                    slot == null || slot.cooldownUntilMs <= 0 ? null : slot.cooldownUntilMs
            ));
        }
        int available = keys.size() - cooldown;
        String status = keys.isEmpty() ? "UNAVAILABLE" : (available > 0 ? "AVAILABLE" : "UNAVAILABLE");
        return new GeminiPoolSnapshot(keys.size(), healthy, cooldown, available, status, views);
    }

    public static GeminiFailureKind classify(Throwable error) {
        if (error instanceof SmartQaException smartQaException
                && smartQaException.errorCode() == ErrorCode.AI_RESPONSE_INVALID) {
            return GeminiFailureKind.SCHEMA;
        }
        String reason = AiCalls.failureReason(error).toLowerCase();
        if (reason.contains("structured output")
                || reason.contains("start_object")
                || reason.contains("deserialize")
                || reason.contains("schema")) {
            return GeminiFailureKind.SCHEMA;
        }
        if (AiCalls.isRateLimited(error)) {
            return GeminiFailureKind.RATE_LIMITED;
        }
        if (AiCalls.isTimeout(error)
                || (error instanceof SmartQaException smartQaException
                && smartQaException.errorCode() == ErrorCode.AI_TIMEOUT)) {
            return GeminiFailureKind.TIMEOUT;
        }
        if (reason.contains("401") || reason.contains("403")) {
            return GeminiFailureKind.AUTH;
        }
        if (reason.contains("http 5") || reason.contains(" http 5")) {
            return GeminiFailureKind.SERVER_ERROR;
        }
        return GeminiFailureKind.UNKNOWN;
    }

    private <T> Mono<T> invoke(
            String operation,
            String model,
            Function<GeminiKeyLease, Mono<T>> invoker,
            List<String> keys,
            int index,
            int attempt,
            Throwable lastError) {
        GeminiRotationPolicy policy = policy();
        int max = policy.maxAttempts(keys.size());
        int chosen = nextHealthy(keys.size(), index, attempt);
        if (chosen < 0 || attempt >= max) {
            TraceLogger.warn("AI", "GEMINI_POOL_EXHAUSTED", "All Gemini API keys failed or are cooling down", TraceMeta.of(
                    "provider", "gemini",
                    "operation", operation,
                    "model", model == null ? "" : model,
                    "keyCount", keys.size(),
                    "attempt", attempt,
                    "reason", lastError == null ? "NO_HEALTHY_KEY" : AiCalls.failureReason(lastError)
            ));
            return Mono.error(exhausted(keys.size(), lastError));
        }
        GeminiKeyLease lease = new GeminiKeyLease(chosen, keys.get(chosen), keys.size(), attempt + 1);
        TraceLogger.info("AI", "GEMINI_REQUEST_STARTED", "Gemini request started", TraceMeta.of(
                "provider", "gemini",
                "operation", operation,
                "model", model == null ? "" : model,
                "keyIndex", lease.displayIndex(),
                "keyCount", lease.keyCount(),
                "attempt", lease.attempt(),
                "rotation", attempt > 0
        ));
        long started = System.nanoTime();
        return Mono.defer(() -> invoker.apply(lease))
                .doOnSuccess(ignored -> {
                    long latencyMs = (System.nanoTime() - started) / 1_000_000;
                    markSuccess(chosen);
                    TraceLogger.info("AI", "GEMINI_REQUEST_SUCCESS", "Gemini request succeeded", TraceMeta.of(
                            "provider", "gemini",
                            "operation", operation,
                            "model", model == null ? "" : model,
                            "keyIndex", lease.displayIndex(),
                            "keyCount", lease.keyCount(),
                            "attempt", lease.attempt(),
                            "latencyMs", latencyMs,
                            "status", "SUCCESS",
                            "finalOutcome", "SUCCESS"
                    ));
                })
                .onErrorResume(error -> {
                    long latencyMs = (System.nanoTime() - started) / 1_000_000;
                    markFailure(chosen, error);
                    TraceLogger.warn("AI", "GEMINI_KEY_FAILED", "Gemini key failed", TraceMeta.of(
                            "provider", "gemini",
                            "operation", operation,
                            "model", model == null ? "" : model,
                            "keyIndex", lease.displayIndex(),
                            "keyCount", lease.keyCount(),
                            "attempt", lease.attempt(),
                            "latencyMs", latencyMs,
                            "status", "FAILED",
                            "failureType", classify(error).name(),
                            "reason", AiCalls.failureReason(error)
                    ));
                    boolean rotatable = AiCalls.isKeyRotatable(error);
                    int nextAttempt = attempt + 1;
                    if (rotatable && nextAttempt < max) {
                        int next = nextHealthy(keys.size(), chosen + 1, nextAttempt);
                        if (next >= 0) {
                            TraceLogger.warn("AI", "GEMINI_KEY_ROTATE", "Gemini key failed; trying next healthy key", TraceMeta.of(
                                    "provider", "gemini",
                                    "operation", operation,
                                    "failedKeyIndex", lease.displayIndex(),
                                    "nextKeyIndex", next + 1,
                                    "keyCount", keys.size(),
                                    "reason", AiCalls.failureReason(error)
                            ));
                            return invoke(operation, model, invoker, keys, next, nextAttempt, error);
                        }
                    }
                    TraceLogger.warn("AI", "GEMINI_POOL_EXHAUSTED", "All Gemini API keys failed or are cooling down", TraceMeta.of(
                            "provider", "gemini",
                            "operation", operation,
                            "model", model == null ? "" : model,
                            "keyCount", keys.size(),
                            "attempt", nextAttempt,
                            "reason", AiCalls.failureReason(error)
                    ));
                    return Mono.error(exhausted(keys.size(), error));
                });
    }

    private boolean selectable(int index) {
        return !isCoolingDown(index);
    }

    private KeySlot slot(int index) {
        return slots.computeIfAbsent(index, ignored -> new KeySlot());
    }

    private static SmartQaException exhausted(int keyCount, Throwable last) {
        String message = "GEMINI_POOL_EXHAUSTED: all " + keyCount + " Gemini API keys failed";
        if (last instanceof SmartQaException smartQaException) {
            return new SmartQaException(ErrorCode.AI_UNAVAILABLE, message, smartQaException);
        }
        return last == null
                ? new SmartQaException(ErrorCode.AI_UNAVAILABLE, message)
                : new SmartQaException(ErrorCode.AI_UNAVAILABLE, message, last);
    }

    private static final class KeySlot {
        private volatile String state = STATE_UNKNOWN;
        private volatile String failureType;
        private volatile int failureCount;
        private volatile long lastFailureAt;
        private volatile long lastSuccessAt;
        private volatile long cooldownUntilMs;
    }
}
