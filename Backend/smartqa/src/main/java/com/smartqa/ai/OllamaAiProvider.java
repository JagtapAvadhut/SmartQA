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
import java.util.Locale;
import java.util.Map;

public class OllamaAiProvider extends AbstractAiProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiProvider.class);

    private final WebClient webClient;
    private final SmartQaProperties properties;

    public OllamaAiProvider(WebClient webClient, ObjectMapper objectMapper, SmartQaProperties properties) {
        super(objectMapper);
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public Mono<AiHealthStatus> healthCheck() {
        String model = properties.getAi().getOllama().getModel();
        String baseUrl = trimSlash(properties.getAi().getOllama().getBaseUrl());
        String host = AiCalls.hostOnly(baseUrl);
        long started = System.nanoTime();
        return webClient.get()
                .uri(baseUrl + "/api/tags")
                .retrieve()
                .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("ollama"))
                .bodyToMono(String.class)
                .timeout(AiCalls.healthTimeout(properties))
                .flatMap(raw -> {
                    if (!modelInstalled(raw, model)) {
                        long latency = (System.nanoTime() - started) / 1_000_000;
                        return Mono.just(AiHealthStatus.unavailable(
                                "ollama", model, host, "MODEL_UNAVAILABLE", latency));
                    }
                    return webClient.get()
                            .uri(baseUrl + "/api/ps")
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("ollama"))
                            .bodyToMono(String.class)
                            .timeout(AiCalls.healthTimeout(properties))
                            .map(ps -> {
                                long latency = (System.nanoTime() - started) / 1_000_000;
                                if (!modelLoaded(ps, model)) {
                                    return AiHealthStatus.cold(
                                            "ollama", model, host, "MODEL_NOT_LOADED", latency);
                                }
                                return AiHealthStatus.available("ollama", model, host, latency);
                            })
                            .onErrorReturn(AiHealthStatus.cold(
                                    "ollama", model, host, "MODEL_NOT_LOADED",
                                    (System.nanoTime() - started) / 1_000_000));
                })
                .onErrorResume(error -> Mono.just(AiHealthStatus.unavailable(
                        "ollama",
                        model,
                        host,
                        AiCalls.isTimeout(error) ? "TIMEOUT" : "CONNECTION_FAILED",
                        (System.nanoTime() - started) / 1_000_000)));
    }

    @Override
    public Mono<String> generateText(AiPrompt prompt) {
        String model = properties.getAi().getOllama().getModel();
        String baseUrl = properties.getAi().getOllama().getBaseUrl();
        Map<String, Object> body = buildRequestBody(prompt, model);
        log.info("ai_request provider=ollama model={} multimodal={}", model, prompt.hasMedia());
        String endpoint = trimSlash(baseUrl) + "/api/chat";
        int promptLength = prompt.textLength() + prompt.mediaBytes();
        return Mono.deferContextual(ctx -> {
            TraceContext.set(TraceContext.from(ctx));
            TraceLogger.info("AI", "AI_PROVIDER_SELECTED", "AI provider selected", TraceMeta.of(
                    "provider", "ollama",
                    "model", model,
                    "endpoint", AiCalls.hostOnly(endpoint),
                    "timeoutSeconds", AiCalls.timeoutSeconds(properties),
                    "screenshotIncluded", prompt.hasMedia(),
                    "mediaBytes", prompt.mediaBytes()
            ));
            TraceLogger.info("AI", "AI_REQUEST_STARTED", "Ollama request started", TraceMeta.of(
                    "provider", "ollama",
                    "model", model,
                    "endpoint", endpoint,
                    "promptLength", promptLength,
                    "jsonOutput", prompt.jsonOutput(),
                    "screenshotIncluded", prompt.hasMedia()
            ));
            TraceLogger.debug("AI", "AI_PROMPT", "Ollama prompt stored", TraceMeta.of(
                    "prompt", TraceLogger.summarizeLarge(prompt.system() + "\n\n" + prompt.user(), "ollama-prompt")
            ));
            long started = System.nanoTime();
            Map<String, Object> fallbackBody = new LinkedHashMap<>(body);
            fallbackBody.remove("format");
            return AiCalls.timed(
                    webClient.post()
                            .uri(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("ollama"))
                            .bodyToMono(String.class)
                            .map(this::readMessage)
                            .onErrorResume(error -> {
                                if (!isHttp400(error) || !prompt.jsonOutput()) {
                                    return Mono.error(error);
                                }
                                TraceLogger.warn("AI", "OLLAMA_REQUEST_FAILURE",
                                        "Ollama HTTP 400 with format=json; retrying without format",
                                        TraceMeta.of("provider", "ollama", "model", model));
                                return webClient.post()
                                        .uri(endpoint)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(fallbackBody)
                                        .retrieve()
                                        .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("ollama"))
                                        .bodyToMono(String.class)
                                        .map(this::readMessage);
                            })
                            .doOnSuccess(text -> {
                                TraceContext.set(TraceContext.from(ctx));
                                long durationMs = (System.nanoTime() - started) / 1_000_000;
                                TraceLogger.info("AI", "AI_REQUEST_COMPLETED", "Ollama HTTP call completed",
                                        durationMs,
                                        TraceMeta.of(
                                                "provider", "ollama",
                                                "model", model,
                                                "responseLength", text == null ? 0 : text.length()
                                        ));
                                TraceLogger.info("AI", "AI_RESPONSE_RECEIVED", "Ollama response received",
                                        durationMs,
                                        TraceMeta.of(
                                                "provider", "ollama",
                                                "model", model,
                                                "responseLength", text == null ? 0 : text.length()
                                        ));
                            })
                            .doOnError(ex -> {
                                if (!AiCalls.isTimeout(ex)) {
                                    TraceLogger.error("AI", "AI_REQUEST_FAILED", "Ollama request failed", ex,
                                            (System.nanoTime() - started) / 1_000_000,
                                            TraceMeta.of(
                                                    "provider", "ollama",
                                                    "model", model,
                                                    "endpoint", AiCalls.hostOnly(endpoint),
                                                    "errorType", ex.getClass().getSimpleName()
                                            ));
                                }
                            }),
                    properties,
                    "ollama",
                    model,
                    endpoint);
        });
    }

    static Map<String, Object> buildRequestBody(AiPrompt prompt, String model) {
        // keep_alive prevents repeated cold loads; options keep local inference deterministic/faster.
        Map<String, Object> options = Map.of(
                "temperature", 0,
                "num_ctx", 4096
        );
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt.user());
        if (prompt.hasMedia()) {
            List<String> images = new ArrayList<>();
            for (AiMediaPart media : prompt.media()) {
                if (media == null || media.isEmpty()) {
                    continue;
                }
                images.add(Base64.getEncoder().encodeToString(media.data()));
            }
            if (!images.isEmpty()) {
                userMessage.put("images", images);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("keep_alive", "30m");
        body.put("options", options);
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompt.system()),
                userMessage
        ));
        if (prompt.jsonOutput()) {
            body.put("format", "json");
        }
        return body;
    }

    private boolean modelInstalled(String raw, String model) {
        return containsModel(raw, model);
    }

    private boolean modelLoaded(String raw, String model) {
        return containsModel(raw, model);
    }

    private boolean containsModel(String raw, String model) {
        if (raw == null || model == null || model.isBlank()) {
            return false;
        }
        try {
            JsonNode models = objectMapper().readTree(raw).path("models");
            if (!models.isArray()) {
                return raw.toLowerCase(Locale.ROOT).contains(model.toLowerCase(Locale.ROOT));
            }
            String wanted = model.toLowerCase(Locale.ROOT);
            for (JsonNode node : models) {
                String name = node.path("name").asText("");
                String id = node.path("model").asText("");
                if (name.toLowerCase(Locale.ROOT).equals(wanted)
                        || id.toLowerCase(Locale.ROOT).equals(wanted)
                        || name.toLowerCase(Locale.ROOT).startsWith(wanted + ":")) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            return raw.toLowerCase(Locale.ROOT).contains(model.toLowerCase(Locale.ROOT));
        }
    }

    private String readMessage(String raw) {
        try {
            JsonNode root = objectMapper().readTree(raw);
            JsonNode content = root.path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "Ollama returned an empty response");
            }
            return content.asText();
        } catch (SmartQaException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "Unable to parse Ollama response", ex);
        }
    }

    private static boolean isHttp400(Throwable error) {
        String reason = error == null ? "" : String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        return reason.contains("400") || reason.contains("bad request");
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
