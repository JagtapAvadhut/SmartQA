package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiKeyPoolTest {

    @Test
    void schemaFailureDoesNotCooldownKey() {
        GeminiKeyPool pool = new GeminiKeyPool();
        pool.markFailure(0, new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "Cannot deserialize String from Object"));
        assertEquals(GeminiFailureKind.SCHEMA, GeminiKeyPool.classify(
                new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "invalid structured output START_OBJECT")));
        assertFalse(pool.isCoolingDown(0));
    }

    @Test
    void skipsCoolingKey() {
        GeminiKeyPool pool = new GeminiKeyPool();
        pool.markFailure(0, new SmartQaException(ErrorCode.AI_RATE_LIMITED, "gemini HTTP 429"));
        int next = pool.nextHealthy(3, 0, 0);
        assertEquals(1, next);
    }

    @Test
    void successAdvancesCursorForFairness() {
        GeminiKeyPool pool = new GeminiKeyPool();
        pool.markSuccess(2);
        assertEquals(3, pool.startIndex(4));
    }

    @Test
    void mergeDropsDuplicatesAndKeepsOrder() {
        assertEquals(
                List.of("k1"),
                SmartQaProperties.Gemini.mergeApiKeys("k1", "", List.of()));
        assertEquals(
                List.of("k1", "k2", "k3"),
                SmartQaProperties.Gemini.mergeApiKeys("k1", "k2,k3", List.of()));
        assertEquals(
                List.of("k1", "k2"),
                SmartQaProperties.Gemini.mergeApiKeys("k1", "k1,k2", List.of()));
        assertEquals(
                List.of("k1", "k2", "k4"),
                SmartQaProperties.Gemini.mergeApiKeys("k1", "k2", List.of("k4")));
        assertEquals(
                List.of("k1", "k2", "k3"),
                SmartQaProperties.Gemini.mergeApiKeys("", "k1,k2,k3", List.of()));
        assertEquals(
                List.of("a", "b", "c"),
                SmartQaProperties.Gemini.mergeApiKeys("a", "b", List.of("c")));
    }

    @Test
    void resolvedKeysMergePrimaryAndCsv() {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey("alpha");
        properties.getAi().getGemini().setApiKeys("beta,gamma");
        List<String> keys = properties.getAi().getGemini().resolvedApiKeys();
        assertEquals("alpha", keys.getFirst());
        assertTrue(keys.contains("beta"));
        assertTrue(keys.contains("gamma"));
        assertEquals(keys.size(), keys.stream().distinct().count());
    }

    @Test
    void roundRobinUsesNextHealthyKey() {
        SmartQaProperties properties = propertiesWithKeys("k1", "k2", "k3");
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(pool.execute("gen", "m", lease -> {
                        used.add(lease.index());
                        return Mono.just("ok");
                    }))
                    .expectNext("ok")
                    .verifyComplete();
        }
        assertEquals(List.of(0, 1, 2), used);
    }

    @Test
    void rateLimitRotatesAndCoolsDown() {
        SmartQaProperties properties = propertiesWithKeys("k1", "k2");
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        AtomicInteger calls = new AtomicInteger();
        StepVerifier.create(pool.execute("gen", "m", lease -> {
                    int n = calls.incrementAndGet();
                    if (lease.index() == 0) {
                        return Mono.error(new SmartQaException(ErrorCode.AI_RATE_LIMITED, "gemini HTTP 429"));
                    }
                    return Mono.just("ok-" + n);
                }))
                .expectNext("ok-2")
                .verifyComplete();
        assertEquals(2, calls.get());
        assertTrue(pool.isCoolingDown(0));
        assertEquals(1, pool.snapshot().cooldownKeys());
    }

    @Test
    void serverErrorAndTimeoutAndAuthRotate() {
        assertEquals(GeminiFailureKind.SERVER_ERROR, GeminiKeyPool.classify(
                new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "gemini HTTP 503")));
        assertEquals(GeminiFailureKind.TIMEOUT, GeminiKeyPool.classify(
                new SmartQaException(ErrorCode.AI_TIMEOUT, "AI provider did not respond within 1 seconds.")));
        assertEquals(GeminiFailureKind.AUTH, GeminiKeyPool.classify(
                new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "gemini HTTP 401")));
        assertTrue(AiCalls.isKeyRotatable(new SmartQaException(ErrorCode.AI_TIMEOUT, "timeout")));
        assertTrue(AiCalls.isKeyRotatable(new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "gemini HTTP 503")));
        assertTrue(AiCalls.isKeyRotatable(new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "gemini HTTP 401")));
        java.net.SocketException reset = new java.net.SocketException("Connection reset");
        assertTrue(AiCalls.isConnectionDrop(reset));
        assertTrue(AiCalls.isTimeout(reset));
        assertTrue(AiCalls.isKeyRotatable(reset));
        assertEquals(GeminiFailureKind.TIMEOUT, GeminiKeyPool.classify(reset));
        assertTrue(AiCalls.isKeyRotatable(new SmartQaException(
                ErrorCode.AI_PROVIDER_ERROR, "AI request failed", reset)));
    }

    @Test
    void connectionResetRotatesToNextHealthyKey() {
        SmartQaProperties properties = propertiesWithKeys("k1", "k2");
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        AtomicInteger calls = new AtomicInteger();
        StepVerifier.create(pool.execute("gen", "m", lease -> {
                    int n = calls.incrementAndGet();
                    if (lease.index() == 0) {
                        return Mono.error(new SmartQaException(
                                ErrorCode.AI_PROVIDER_ERROR,
                                "AI request failed",
                                new java.net.SocketException("Connection reset")));
                    }
                    return Mono.just("ok-" + n);
                }))
                .expectNext("ok-2")
                .verifyComplete();
        assertEquals(2, calls.get());
    }

    @Test
    void cooldownExpiresAndKeyBecomesRetryable() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SmartQaProperties properties = propertiesWithKeys("k1", "k2");
        GeminiKeyPool pool = new GeminiKeyPool(properties, now::get);
        pool.markFailure(0, new SmartQaException(ErrorCode.AI_RATE_LIMITED, "gemini HTTP 429"));
        assertTrue(pool.isCoolingDown(0));
        now.addAndGet(61_000);
        assertFalse(pool.isCoolingDown(0));
        assertEquals(GeminiKeyPool.STATE_AVAILABLE_FOR_RETRY, pool.snapshot().keys().getFirst().state());
        assertEquals(0, pool.nextHealthy(2, 0, 0));
    }

    @Test
    void exhaustedPoolReturnsUnavailable() {
        SmartQaProperties properties = propertiesWithKeys("k1", "k2");
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        StepVerifier.create(pool.execute("gen", "m", lease ->
                        Mono.error(new SmartQaException(ErrorCode.AI_RATE_LIMITED, "gemini HTTP 429"))))
                .expectErrorMatches(error -> error instanceof SmartQaException ex
                        && ex.errorCode() == ErrorCode.AI_UNAVAILABLE
                        && ex.getMessage().contains("GEMINI_POOL_EXHAUSTED"))
                .verify();
        assertTrue(pool.snapshot().cooldownKeys() >= 2);
        assertEquals("UNAVAILABLE", pool.snapshot().status());
    }

    @Test
    void snapshotAndLeaseNeverContainSecret() throws Exception {
        String secret = "super-secret-pool-value";
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey(secret);
        properties.getAi().getGemini().setApiKeys("second-secret");
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        pool.markFailure(0, new SmartQaException(ErrorCode.AI_RATE_LIMITED, "gemini HTTP 429"));
        String json = JsonMapper.builder().build().writeValueAsString(pool.snapshot());
        assertFalse(json.contains(secret));
        assertFalse(json.contains("second-secret"));
        assertTrue(json.contains("configuredKeys"));
        GeminiKeyLease lease = new GeminiKeyLease(0, secret, 2, 1);
        assertFalse(lease.toString().contains(secret));
        assertTrue(lease.toString().contains("keyIndex=1"));
        AiHealthStatus health = AiHealthStatus.available("gemini", "m", "host", 1)
                .withKeyCounts(2, 1, 1);
        String healthJson = JsonMapper.builder().build().writeValueAsString(health);
        assertFalse(healthJson.contains(secret));
        assertTrue(healthJson.contains("configuredKeys"));
        assertEquals(2, health.configuredKeys());
        assertEquals(1, health.healthyKeys());
        assertEquals(1, health.cooldownKeys());
    }

    @Test
    void rotationPolicyComesFromConfig() {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().getRotation().setCooldownSeconds(90);
        properties.getAi().getGemini().getRotation().setTransientCooldownSeconds(20);
        properties.getAi().getGemini().getRotation().setInvalidKeyCooldownSeconds(400);
        properties.getAi().getGemini().getRotation().setMaxKeyAttempts("2");
        GeminiRotationPolicy policy = GeminiRotationPolicy.from(properties);
        assertEquals(90, policy.cooldownFor(GeminiFailureKind.RATE_LIMITED).toSeconds());
        assertEquals(20, policy.cooldownFor(GeminiFailureKind.TIMEOUT).toSeconds());
        assertEquals(400, policy.cooldownFor(GeminiFailureKind.AUTH).toSeconds());
        assertEquals(2, policy.maxAttempts(8));
    }

    @Test
    void disabledRotationUsesSingleAttempt() {
        SmartQaProperties properties = propertiesWithKeys("k1", "k2", "k3");
        properties.getAi().getGemini().getRotation().setEnabled(false);
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        AtomicInteger calls = new AtomicInteger();
        StepVerifier.create(pool.execute("gen", "m", lease -> {
                    calls.incrementAndGet();
                    return Mono.error(new SmartQaException(ErrorCode.AI_RATE_LIMITED, "gemini HTTP 429"));
                }))
                .expectErrorMatches(error -> error instanceof SmartQaException ex
                        && ex.errorCode() == ErrorCode.AI_UNAVAILABLE)
                .verify();
        assertEquals(1, calls.get());
    }

    @Test
    void skipsStartIndexWhenThatKeyIsCooling() {
        SmartQaProperties properties = propertiesWithKeys("k1", "k2", "k3");
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        pool.markSuccess(0);
        pool.markFailure(1, new SmartQaException(ErrorCode.AI_RATE_LIMITED, "gemini HTTP 429"));
        List<Integer> used = new ArrayList<>();
        StepVerifier.create(pool.execute("gen", "m", lease -> {
                    used.add(lease.index());
                    return Mono.just("ok");
                }))
                .expectNext("ok")
                .verifyComplete();
        assertEquals(List.of(2), used);
    }

    private static SmartQaProperties propertiesWithKeys(String... keys) {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey(keys[0]);
        if (keys.length > 1) {
            properties.getAi().getGemini().setApiKeys(String.join(",", java.util.Arrays.copyOfRange(keys, 1, keys.length)));
        }
        return properties;
    }
}
