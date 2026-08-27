package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.event.RunCorrelation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class FallbackAiProvider implements AiProvider {

    private final Map<String, AiProvider> providers;
    private final SmartQaProperties properties;
    private final ProgressEventHub eventHub;

    public FallbackAiProvider(Map<String, AiProvider> providers, SmartQaProperties properties) {
        this(providers, properties, null);
    }

    public FallbackAiProvider(Map<String, AiProvider> providers, SmartQaProperties properties, ProgressEventHub eventHub) {
        this.providers = providers;
        this.properties = properties;
        this.eventHub = eventHub;
    }

    @Override
    public String id() {
        return primaryName();
    }

    public String primaryName() {
        String configured = configuredPrimaryName();
        if (isCloudProviderWithoutKey(configured)) {
            String fallback = firstNonBlank(properties.getAi().getFallbackProvider(), "ollama");
            if (!fallback.isBlank() && !fallback.equalsIgnoreCase(configured)) {
                return fallback;
            }
            return "ollama";
        }
        return configured;
    }

    public String fallbackName() {
        String effectivePrimary = primaryName();
        String fallback = firstNonBlank(properties.getAi().getFallbackProvider(), "");
        if (fallback.isBlank() || fallback.equalsIgnoreCase(effectivePrimary)) {
            return "";
        }
        if (isCloudProviderWithoutKey(fallback)) {
            return "";
        }
        return fallback;
    }

    private String configuredPrimaryName() {
        return firstNonBlank(
                properties.getAi().getPrimaryProvider(),
                properties.getAi().getProvider(),
                "ollama");
    }

    private boolean isCloudProviderWithoutKey(String provider) {
        if (provider == null) {
            return false;
        }
        String id = provider.trim().toLowerCase(Locale.ROOT);
        if ("gemini".equals(id)) {
            return properties.getAi().getGemini().resolvedApiKeys().isEmpty();
        }
        if ("openai".equals(id) || "openai-compatible".equals(id)) {
            String key = properties.getAi().getOpenaiCompatible().getApiKey();
            return key == null || key.isBlank();
        }
        return false;
    }

    public boolean fallbackConfigured() {
        return resolve(fallbackName()) != null;
    }

    @Override
    public Mono<String> generateText(AiPrompt prompt) {
        return execute(provider -> provider.generateText(prompt));
    }

    @Override
    public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
        return execute(provider -> provider.generateStructuredOutput(prompt, type));
    }

    /**
     * Primary diagnosis plus optional second opinion from the fallback provider.
     * Used for important failures when consensus mode is enabled.
     * Failover behavior is unchanged for normal {@link #generateStructuredOutput}.
     */
    public <T> Mono<DualOpinion<T>> generateStructuredDual(AiPrompt prompt, Class<T> type) {
        AiProvider primary = require(primaryName());
        AiProvider secondary = resolve(fallbackName());
        return Mono.deferContextual(ctx -> {
            String traceId = TraceContext.from(ctx);
            TraceContext.set(traceId);
            return skipReason(primary).flatMap(skip -> {
                TraceContext.set(traceId);
                if (!skip.isBlank()) {
                    if (secondary == null) {
                        return Mono.error(unavailable(primary.id(), skip, null, null));
                    }
                    TraceLogger.info("AI", "AI_FALLBACK_STARTED", "Primary unusable; dual uses fallback only", TraceMeta.of(
                            "from", primary.id(),
                            "to", secondary.id(),
                            "reason", skip
                    ));
                    return attempt(p -> p.generateStructuredOutput(prompt, type), secondary)
                            .map(result -> new DualOpinion<>(result, secondary.id(), null, null, false));
                }
                return attempt(p -> p.generateStructuredOutput(prompt, type), primary)
                        .flatMap(primaryResult -> {
                            TraceContext.set(traceId);
                            if (secondary == null) {
                                return Mono.just(new DualOpinion<>(primaryResult, primary.id(), null, null, false));
                            }
                            return attempt(p -> p.generateStructuredOutput(prompt, type), secondary)
                                    .map(secondaryResult -> new DualOpinion<>(
                                            primaryResult, primary.id(), secondaryResult, secondary.id(), true))
                                    .onErrorResume(error -> {
                                        TraceContext.set(traceId);
                                        TraceLogger.warn("AI", "AI_SECOND_OPINION_FAILED", "Second opinion unavailable", TraceMeta.of(
                                                "provider", secondary.id(),
                                                "reason", AiCalls.failureReason(error)
                                        ));
                                        return Mono.just(new DualOpinion<>(
                                                primaryResult, primary.id(), null, null, true));
                                    });
                        })
                        .onErrorResume(error -> {
                            TraceContext.set(traceId);
                            if (!AiCalls.isFallbackable(error) || secondary == null) {
                                return Mono.error(error);
                            }
                            TraceLogger.error("AI", "AI_REQUEST_FAILED", "Primary AI provider failed during dual", error,
                                    null,
                                    TraceMeta.of(
                                            "provider", primary.id(),
                                            "reason", AiCalls.failureReason(error),
                                            "fallbackConfigured", true
                                    ));
                            return attempt(p -> p.generateStructuredOutput(prompt, type), secondary)
                                    .map(result -> new DualOpinion<>(result, secondary.id(), null, null, false));
                        });
            });
        });
    }

    public record DualOpinion<T>(
            T primary,
            String primaryProvider,
            T secondary,
            String secondaryProvider,
            boolean dualAttempted
    ) {
        public boolean hasSecondOpinion() {
            return secondary != null;
        }
    }

    @Override
    public Mono<AiHealthStatus> healthCheck() {
        AiProvider primary = require(primaryName());
        return primary.healthCheck();
    }

    public Mono<AiHealthSnapshot> healthAll() {
        List<String> ids = new ArrayList<>();
        ids.add(primaryName());
        String fallback = fallbackName();
        if (!fallback.isBlank() && !ids.contains(fallback)) {
            ids.add(fallback);
        }
        return Flux.fromIterable(ids)
                .concatMap(id -> {
                    AiProvider provider = resolve(id);
                    if (provider == null) {
                        return Mono.just(AiHealthStatus.unavailable(id, null, null, "NOT_CONFIGURED", 0));
                    }
                    return provider.healthCheck()
                            .onErrorReturn(AiHealthStatus.unavailable(id, null, null, "HEALTH_CHECK_FAILED", 0));
                })
                .collectList()
                .map(list -> new AiHealthSnapshot(primaryName(), fallback.isBlank() ? null : fallback, list));
    }

    private <T> Mono<T> execute(Function<AiProvider, Mono<T>> call) {
        AiProvider primary = require(primaryName());
        AiProvider fallback = resolve(fallbackName());
        return Mono.deferContextual(ctx -> {
            String traceId = TraceContext.from(ctx);
            TraceContext.set(traceId);
            RunCorrelation.applyContext(ctx);
            return skipReason(primary)
                    .flatMap(skip -> {
                        TraceContext.set(traceId);
                        if (!skip.isBlank()) {
                            TraceLogger.error("AI", "AI_REQUEST_FAILED", "Primary AI provider is not usable",
                                    new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, skip),
                                    null,
                                    TraceMeta.of(
                                            "provider", primary.id(),
                                            "reason", skip,
                                            "fallbackConfigured", fallback != null
                                    ));
                            if (fallback == null) {
                                return Mono.error(unavailable(primary.id(), skip, null, null));
                            }
                            return fallbackCall(call, primary, fallback, skip, traceId);
                        }
                        return attempt(call, primary)
                                .onErrorResume(error -> {
                                    TraceContext.set(traceId);
                                    if (!AiCalls.isFallbackable(error) || fallback == null) {
                                        return Mono.error(error);
                                    }
                                    String reason = AiCalls.failureReason(error);
                                    TraceLogger.error("AI", "AI_REQUEST_FAILED", "Primary AI provider failed", error,
                                            null,
                                            TraceMeta.of(
                                                    "provider", primary.id(),
                                                    "reason", reason,
                                                    "fallbackConfigured", true
                                            ));
                                    return fallbackCall(call, primary, fallback, reason, traceId);
                                });
                    });
        });
    }

    private <T> Mono<T> attempt(Function<AiProvider, Mono<T>> call, AiProvider provider) {
        return attemptWithRetry(call, provider, 0);
    }

    private <T> Mono<T> attemptWithRetry(Function<AiProvider, Mono<T>> call, AiProvider provider, int retryIndex) {
        return call.apply(provider).onErrorResume(error -> {
            int configured = Math.max(0, properties.getAi().getMaxRetries());
            int limit = configured;
            if (AiCalls.isRateLimited(error) && configured >= 1) {
                limit = Math.max(configured, 2);
            }
            if (retryIndex >= limit || (!AiCalls.isRetryable(error) && !AiCalls.isRateLimited(error))) {
                return Mono.error(error);
            }
            long delaySec = AiCalls.isRateLimited(error) ? (retryIndex == 0 ? 2L : 4L) : 2L;
            TraceLogger.warn("AI", "AI_REQUEST_RETRY", "Retrying AI provider after transient failure", TraceMeta.of(
                    "provider", provider.id(),
                    "reason", AiCalls.failureReason(error),
                    "retryIndex", retryIndex + 1,
                    "delaySec", delaySec
            ));
            return Mono.delay(Duration.ofSeconds(delaySec))
                    .then(attemptWithRetry(call, provider, retryIndex + 1));
        });
    }

    private <T> Mono<T> fallbackCall(
            Function<AiProvider, Mono<T>> call,
            AiProvider primary,
            AiProvider fallback,
            String reason,
            String traceId) {
        TraceContext.set(traceId);
        TraceLogger.info("AI", "AI_FALLBACK_STARTED", "Falling back to alternate AI provider", TraceMeta.of(
                "from", primary.id(),
                "to", fallback.id(),
                "reason", reason
        ));
        if ("ollama".equalsIgnoreCase(fallback.id())) {
            TraceLogger.info("AI", "OLLAMA_FALLBACK_STARTED", "Gemini unavailable; trying Ollama", TraceMeta.of(
                    "from", primary.id(),
                    "to", fallback.id(),
                    "reason", reason
            ));
        }
        emitProgress("AI_FALLBACK_STARTED", "Switching AI provider (" + primary.id() + " → " + fallback.id() + ")",
                primary, fallback, reason);
        if ("ollama".equalsIgnoreCase(fallback.id())) {
            emitProgress("OLLAMA_FALLBACK_STARTED", "Gemini unavailable; trying local Ollama. First load can take a few minutes.",
                    primary, fallback, reason);
        }
        return attempt(call, fallback)
                .doOnSuccess(ignored -> {
                    TraceContext.set(traceId);
                    TraceLogger.info("AI", "AI_FALLBACK_SUCCEEDED", "Fallback AI provider succeeded", TraceMeta.of(
                            "from", primary.id(),
                            "to", fallback.id()
                    ));
                })
                .onErrorMap(error -> unavailable(
                        primary.id(),
                        reason,
                        fallback.id(),
                        AiCalls.failureReason(error)));
    }

    private void emitProgress(String type, String message, AiProvider primary, AiProvider fallback, String reason) {
        if (eventHub == null) {
            return;
        }
        UUID testCaseId = RunCorrelation.testCaseId();
        UUID pipelineId = RunCorrelation.pipelineRunId();
        if (testCaseId == null && pipelineId == null) {
            return;
        }
        Map<String, Object> details = new HashMap<>();
        details.put("from", primary.id());
        details.put("to", fallback.id());
        details.put("reason", reason);
        String channel = testCaseId != null
                ? ProgressEventHub.generationChannel(testCaseId)
                : ProgressEventHub.pipelineChannel(pipelineId);
        eventHub.emit(channel, ProgressEvent.generation(type, message, testCaseId, details));
    }

    private Mono<String> skipReason(AiProvider provider) {
        // Config-only skip on the generate path. HTTP health is preflight's job and must not
        // duplicate provider calls (extra 429s) before generateText.
        if (isCloudProviderWithoutKey(provider.id())) {
            return Mono.just("NOT_CONFIGURED");
        }
        return Mono.just("");
    }

    private AiProvider require(String name) {
        AiProvider provider = resolve(name);
        if (provider == null) {
            throw new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "Unknown AI provider: " + name);
        }
        return provider;
    }

    private AiProvider resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return providers.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private static SmartQaException unavailable(
            String primary,
            String primaryFailure,
            String fallback,
            String fallbackFailure) {
        String message;
        if (fallback == null || fallback.isBlank()) {
            message = "AI provider " + primary + " is unavailable (" + primaryFailure
                    + ") and no fallback has been configured";
        } else {
            message = "AI providers unavailable. primary=" + primary
                    + " primaryFailure=" + primaryFailure
                    + " fallback=" + fallback
                    + " fallbackFailure=" + fallbackFailure;
        }
        return new SmartQaException(ErrorCode.AI_PROVIDERS_UNAVAILABLE, message);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }
}
