package com.smartqa.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeminiAiProvider extends AbstractAiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);

    private final WebClient webClient;
    private final SmartQaProperties properties;
    private final GeminiKeyPool keyPool;

    public GeminiAiProvider(WebClient webClient, ObjectMapper objectMapper, SmartQaProperties properties) {
        this(webClient, objectMapper, properties, new GeminiKeyPool(properties));
    }

    public GeminiAiProvider(
            WebClient webClient,
            ObjectMapper objectMapper,
            SmartQaProperties properties,
            GeminiKeyPool keyPool) {
        super(objectMapper);
        this.webClient = webClient;
        this.properties = properties;
        this.keyPool = keyPool == null ? new GeminiKeyPool(properties) : keyPool;
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public Mono<AiHealthStatus> healthCheck() {
        String model = properties.getAi().getGemini().getModel();
        String host = AiCalls.hostOnly(properties.getAi().getGemini().getBaseUrl());
        GeminiPoolSnapshot snapshot = keyPool.snapshot();
        if (snapshot.configuredKeys() == 0) {
            return Mono.just(AiHealthStatus.unavailable("gemini", model, host, "NOT_CONFIGURED", 0)
                    .withKeyCounts(0, 0, 0));
        }
        if (snapshot.availableKeys() == 0) {
            return Mono.just(AiHealthStatus.unavailable("gemini", model, host, "ALL_KEYS_COOLDOWN", 0)
                    .withKeyCounts(snapshot.configuredKeys(), snapshot.healthyKeys(), snapshot.cooldownKeys()));
        }
        List<String> keys = keyPool.keys();
        int index = keyPool.nextHealthy(keys.size(), keyPool.startIndex(keys.size()), 0);
        if (index < 0) {
            return Mono.just(AiHealthStatus.unavailable("gemini", model, host, "ALL_KEYS_COOLDOWN", 0)
                    .withKeyCounts(snapshot.configuredKeys(), snapshot.healthyKeys(), snapshot.cooldownKeys()));
        }
        long started = System.nanoTime();
        String uri = trimSlash(properties.getAi().getGemini().getBaseUrl()) + "/v1beta/models/" + model;
        return webClient.get()
                .uri(uri)
                .header("x-goog-api-key", keys.get(index))
                .retrieve()
                .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("gemini"))
                .bodyToMono(String.class)
                .timeout(AiCalls.healthTimeout(properties))
                .map(ignored -> AiHealthStatus.available(
                                "gemini", model, host, (System.nanoTime() - started) / 1_000_000)
                        .withKeyCounts(snapshot.configuredKeys(), snapshot.healthyKeys(), snapshot.cooldownKeys()))
                .onErrorResume(error -> {
                    String reason = AiCalls.isRateLimited(error)
                            ? "GEMINI_RATE_LIMITED"
                            : (AiCalls.isTimeout(error) ? "TIMEOUT" : AiCalls.failureReason(error));
                    return Mono.just(AiHealthStatus.unavailable(
                                    "gemini",
                                    model,
                                    host,
                                    reason,
                                    (System.nanoTime() - started) / 1_000_000)
                            .withKeyCounts(snapshot.configuredKeys(), snapshot.healthyKeys(), snapshot.cooldownKeys()));
                });
    }

    @Override
    public Mono<String> generateText(AiPrompt prompt) {
        String model = properties.getAi().getGemini().getModel();
        String uri = trimSlash(properties.getAi().getGemini().getBaseUrl())
                + "/v1beta/models/" + model + ":generateContent";
        Map<String, Object> body = buildRequestBody(prompt);
        int keyCount = keyPool.keys().size();
        log.info("ai_request provider=gemini model={} keyCount={} multimodal={}",
                model, keyCount, prompt.hasMedia());
        int promptLength = prompt.textLength() + prompt.mediaBytes();
        return Mono.deferContextual(ctx -> {
            TraceContext.set(TraceContext.from(ctx));
            TraceLogger.info("AI", "AI_PROVIDER_SELECTED", "AI provider selected", TraceMeta.of(
                    "provider", "gemini",
                    "model", model,
                    "endpoint", AiCalls.hostOnly(uri),
                    "keyCount", keyCount,
                    "preferredKeyIndex", keyPool.startIndex(Math.max(1, keyCount)) + 1,
                    "timeoutSeconds", prompt.hasMedia()
                            ? AiCalls.multimodalTimeoutSeconds(properties)
                            : AiCalls.timeoutSeconds(properties),
                    "screenshotIncluded", prompt.hasMedia(),
                    "mediaBytes", prompt.mediaBytes()
            ));
            return keyPool.execute("generate", model, lease -> {
                TraceLogger.info("AI", "AI_REQUEST_STARTED", "Gemini request started", TraceMeta.of(
                        "provider", "gemini",
                        "model", model,
                        "endpoint", uri,
                        "promptLength", promptLength,
                        "keyIndex", lease.displayIndex(),
                        "keyCount", lease.keyCount(),
                        "attempt", lease.attempt()
                ));
                long started = System.nanoTime();
                int callTimeout = prompt.hasMedia()
                        ? AiCalls.multimodalTimeoutSeconds(properties)
                        : AiCalls.timeoutSeconds(properties);
                return AiCalls.timed(
                        webClient.post()
                                .uri(uri)
                                .header("x-goog-api-key", lease.apiKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("gemini"))
                                .bodyToMono(String.class)
                                .map(this::readText)
                                .doOnSuccess(text -> TraceLogger.info(
                                        "AI",
                                        "AI_RESPONSE_RECEIVED",
                                        "Gemini response received",
                                        (System.nanoTime() - started) / 1_000_000,
                                        TraceMeta.of(
                                                "provider", "gemini",
                                                "model", model,
                                                "keyIndex", lease.displayIndex(),
                                                "responseLength", text == null ? 0 : text.length()
                                        ))),
                        properties,
                        "gemini",
                        model,
                        uri,
                        callTimeout);
            });
        });
    }

    static Map<String, Object> buildRequestBody(AiPrompt prompt) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt.system() + "\n\n" + prompt.user()));
        if (prompt.hasMedia()) {
            for (AiMediaPart media : prompt.media()) {
                if (media == null || media.isEmpty()) {
                    continue;
                }
                Map<String, Object> inline = new LinkedHashMap<>();
                inline.put("mime_type", media.mimeType());
                inline.put("data", Base64.getEncoder().encodeToString(media.data()));
                parts.add(Map.of("inline_data", inline));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", parts)));
        if (prompt.jsonOutput()) {
            body.put("generationConfig", Map.of("responseMimeType", "application/json"));
        }
        return body;
    }

    private String readText(String raw) {
        try {
            JsonNode root = objectMapper().readTree(raw);
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "Gemini returned an empty response");
            }
            JsonNode usage = root.path("usageMetadata");
            if (!usage.isMissingNode()) {
                TokenUsageTracker.record(
                        usage.path("promptTokenCount").asInt(0),
                        usage.path("candidatesTokenCount").asInt(0));
            }
            return text.asText();
        } catch (SmartQaException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "Unable to parse Gemini response", ex);
        }
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
