package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class AiCalls {

    private AiCalls() {
    }

    public static String awaitText(AiProvider provider, AiPrompt prompt, int timeoutSeconds) {
        if (provider == null) {
            throw new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "AI provider unavailable");
        }
        int seconds = Math.max(1, timeoutSeconds);
        // Playwright worker threads are blocking by design. Isolate WebClient on boundedElastic
        // and bound the wait so the event loop is never used for this join.
        return provider.generateText(prompt)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(seconds))
                .onErrorMap(error -> mapError(error, seconds, provider.id()))
                .block(Duration.ofSeconds(seconds + 5));
    }

    public static int timeoutSeconds(SmartQaProperties properties) {
        int configured = properties == null ? 60 : properties.getAi().getTimeoutSeconds();
        return Math.max(1, configured);
    }

    /**
     * Screenshot/multimodal Gemini calls must stay short so key rotation can occur
     * inside the outer wait. Oversized payloads plus a 3–7 minute wait exhaust the run.
     */
    public static int multimodalTimeoutSeconds(SmartQaProperties properties) {
        return Math.max(20, Math.min(90, timeoutSeconds(properties)));
    }

    /**
     * Intent understanding timeout: shorter than general AI and browser execution.
     */
    public static int intentTimeoutSeconds(SmartQaProperties properties) {
        int general = timeoutSeconds(properties);
        int execution = properties == null ? 180 : properties.getExecution().getTimeoutSeconds();
        int intent = properties == null ? 45 : properties.getAi().getIntentTimeoutSeconds();
        if (intent <= 0) {
            intent = 45;
        }
        int cap = Math.max(8, Math.min(general, Math.max(15, execution - 30)));
        return Math.max(8, Math.min(intent, cap));
    }

    public static int connectTimeoutMillis(SmartQaProperties properties) {
        int seconds = properties == null ? 10 : properties.getAi().getConnectTimeoutSeconds();
        return Math.max(1, seconds) * 1000;
    }

    public static Duration healthTimeout(SmartQaProperties properties) {
        int seconds = Math.min(8, timeoutSeconds(properties));
        return Duration.ofSeconds(Math.max(2, seconds));
    }

    public static Function<ClientResponse, Mono<? extends Throwable>> errorStatus(String provider) {
        return response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(ignored -> {
                    HttpStatusCode status = response.statusCode();
                    ErrorCode code = status.value() == 429 ? ErrorCode.AI_RATE_LIMITED : ErrorCode.AI_PROVIDER_ERROR;
                    return Mono.error(new SmartQaException(
                            code,
                            provider + " HTTP " + status.value()));
                });
    }

    public static <T> Mono<T> timed(
            Mono<T> call,
            SmartQaProperties properties,
            String provider,
            String model,
            String endpoint) {
        return timed(call, properties, provider, model, endpoint, timeoutSeconds(properties));
    }

    public static <T> Mono<T> timed(
            Mono<T> call,
            SmartQaProperties properties,
            String provider,
            String model,
            String endpoint,
            int timeoutSeconds) {
        int seconds = Math.max(1, timeoutSeconds);
        long timeoutMs = seconds * 1000L;
        return Mono.deferContextual(ctx -> {
            String traceId = TraceContext.from(ctx);
            TraceContext.set(traceId);
            long started = System.nanoTime();
            return call
                    .doOnSubscribe(ignored -> TraceContext.set(traceId))
                    .timeout(Duration.ofSeconds(seconds))
                    .doOnEach(signal -> TraceContext.set(traceId))
                    .doOnCancel(() -> {
                        TraceContext.set(traceId);
                        TraceLogger.warn("AI", "AI_REQUEST_CANCELLED", "AI provider call cancelled", TraceMeta.of(
                                "provider", provider,
                                "model", model,
                                "endpoint", hostOnly(endpoint),
                                "timeoutMs", timeoutMs
                        ));
                    })
                    .doOnError(error -> {
                        TraceContext.set(traceId);
                        long durationMs = (System.nanoTime() - started) / 1_000_000;
                        if (isTimeout(error)) {
                            TraceLogger.error(
                                    "AI",
                                    "AI_REQUEST_TIMEOUT",
                                    "AI provider did not respond within " + seconds + " seconds.",
                                    error,
                                    durationMs,
                                    TraceMeta.of(
                                            "provider", provider,
                                            "model", model,
                                            "endpoint", hostOnly(endpoint),
                                            "timeoutMs", timeoutMs,
                                            "errorType", error.getClass().getName(),
                                            "message", error.getMessage()
                                    ));
                        }
                    })
                    .onErrorMap(error -> mapError(error, seconds, provider));
        });
    }

    public static boolean isTimeout(Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof TimeoutException || error instanceof ReadTimeoutException) {
            return true;
        }
        String name = error.getClass().getName();
        if (name.contains("Timeout") || name.contains("ReadTimeout")) {
            return true;
        }
        if (isConnectionDrop(error)) {
            return true;
        }
        return isTimeout(error.getCause());
    }

    /**
     * Dropped TLS/TCP sessions (connection reset, premature close) are transient and must
     * rotate to the next Gemini key. They are not pool exhaustion.
     */
    public static boolean isConnectionDrop(Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof java.io.IOException) {
            String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
            String name = error.getClass().getName().toLowerCase();
            if (message.contains("connection reset")
                    || message.contains("broken pipe")
                    || message.contains("connection abort")
                    || message.contains("premature")
                    || name.contains("prematureclose")
                    || name.contains("abortedexception")) {
                return true;
            }
        }
        String name = error.getClass().getName();
        if (name.contains("PrematureClose") || name.contains("AbortedException")) {
            return true;
        }
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        if (message.contains("connection reset") || message.contains("premature close")) {
            return true;
        }
        return false;
    }

    public static boolean isRateLimited(Throwable error) {
        if (error == null) {
            return false;
        }
        String reason = failureReason(error);
        return reason.contains("429") || reason.contains("RATE_LIMIT");
    }

    public static boolean isRetryable(Throwable error) {
        if (error instanceof SmartQaException smartQaException) {
            if (smartQaException.errorCode() == ErrorCode.AI_TIMEOUT) {
                return false;
            }
            String message = smartQaException.getMessage() == null ? "" : smartQaException.getMessage();
            return message.contains(" HTTP 5") || message.contains(" HTTP 429");
        }
        if (error instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return false;
    }

    /** True when another Gemini API key in the pool should be tried. */
    public static boolean isKeyRotatable(Throwable error) {
        if (error == null) {
            return false;
        }
        if (isTimeout(error)) {
            return true;
        }
        if (error instanceof SmartQaException smartQaException) {
            if (smartQaException.errorCode() == ErrorCode.AI_TIMEOUT
                    || smartQaException.errorCode() == ErrorCode.AI_RATE_LIMITED) {
                return true;
            }
            String message = smartQaException.getMessage() == null ? "" : smartQaException.getMessage();
            if (message.contains(" HTTP 429")
                    || message.contains(" HTTP 401")
                    || message.contains(" HTTP 403")
                    || message.contains(" HTTP 5")
                    || message.toLowerCase().contains("connection failed")
                    || message.toLowerCase().contains("connection reset")) {
                return true;
            }
            // Wrapped IO failures must still rotate; do not treat unknown SmartQaException as terminal.
            if (isKeyRotatable(smartQaException.getCause())) {
                return true;
            }
        }
        if (error instanceof WebClientResponseException responseException) {
            int code = responseException.getStatusCode().value();
            return code == 401 || code == 403 || code == 429 || responseException.getStatusCode().is5xxServerError();
        }
        if (error instanceof WebClientRequestException) {
            return true;
        }
        return isKeyRotatable(error.getCause());
    }

    public static boolean isFallbackable(Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof SmartQaException smartQaException) {
            return switch (smartQaException.errorCode()) {
                case AI_TIMEOUT, AI_PROVIDER_ERROR, AI_RESPONSE_INVALID, AI_RATE_LIMITED, AI_UNAVAILABLE -> true;
                default -> false;
            };
        }
        return isTimeout(error)
                || error instanceof WebClientRequestException
                || error instanceof WebClientResponseException
                || isFallbackable(error.getCause());
    }

    public static String failureReason(Throwable error) {
        if (isTimeout(error)) {
            return "TIMEOUT";
        }
        if (error instanceof WebClientRequestException) {
            return "CONNECTION_FAILED";
        }
        if (error instanceof SmartQaException smartQaException) {
            if (smartQaException.errorCode() == ErrorCode.AI_RATE_LIMITED) {
                return "RATE_LIMIT";
            }
            if (smartQaException.errorCode() == ErrorCode.AI_RESPONSE_INVALID) {
                return "INVALID_RESPONSE";
            }
            String message = smartQaException.getMessage() == null ? "" : smartQaException.getMessage();
            if (message.contains("HTTP ")) {
                return message;
            }
            return smartQaException.errorCode().name();
        }
        return error == null ? "UNKNOWN" : error.getClass().getSimpleName();
    }

    public static String hostOnly(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        int scheme = endpoint.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        int slash = endpoint.indexOf('/', start);
        return slash < 0 ? endpoint : endpoint.substring(0, slash);
    }

    private static Throwable mapError(Throwable error, int seconds, String provider) {
        if (error instanceof SmartQaException smartQaException) {
            return smartQaException;
        }
        if (isTimeout(error)) {
            return new SmartQaException(
                    ErrorCode.AI_TIMEOUT,
                    "AI provider did not respond within " + seconds + " seconds.",
                    error);
        }
        if (error instanceof WebClientRequestException) {
            return new SmartQaException(
                    ErrorCode.AI_PROVIDER_ERROR,
                    provider + " connection failed",
                    error);
        }
        return new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "AI request failed", error);
    }
}
