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
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class OpenAiCompatibleAiProvider extends AbstractAiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiProvider.class);

    private final WebClient webClient;
    private final SmartQaProperties properties;

    public OpenAiCompatibleAiProvider(WebClient webClient, ObjectMapper objectMapper, SmartQaProperties properties) {
        super(objectMapper);
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "openai-compatible";
    }

    @Override
    public Mono<String> generateText(AiPrompt prompt) {
        var config = properties.getAi().getOpenaiCompatible();
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            return Mono.error(new SmartQaException(
                    ErrorCode.AI_PROVIDER_ERROR,
                    "OPENAI_COMPATIBLE_BASE_URL is not configured"));
        }
        String uri = trimSlash(config.getBaseUrl()) + "/chat/completions";
        Map<String, Object> body;
        if (prompt.jsonOutput()) {
            body = Map.of(
                    "model", config.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", prompt.system()),
                            Map.of("role", "user", "content", prompt.user())
                    ),
                    "response_format", Map.of("type", "json_object")
            );
        } else {
            body = Map.of(
                    "model", config.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", prompt.system()),
                            Map.of("role", "user", "content", prompt.user())
                    )
            );
        }
        log.info("ai_request provider=openai-compatible model={}", config.getModel());
        int promptLength = prompt.system().length() + prompt.user().length();
        return Mono.deferContextual(ctx -> {
            TraceContext.set(TraceContext.from(ctx));
            TraceLogger.info("AI", "AI_PROVIDER_SELECTED", "AI provider selected", TraceMeta.of(
                    "provider", "openai-compatible",
                    "model", config.getModel(),
                    "endpoint", uri,
                    "timeoutSeconds", AiCalls.timeoutSeconds(properties)
            ));
            TraceLogger.info("AI", "AI_REQUEST_STARTED", "OpenAI-compatible request started", TraceMeta.of(
                    "provider", "openai-compatible",
                    "model", config.getModel(),
                    "endpoint", uri,
                    "promptLength", promptLength,
                    "jsonOutput", prompt.jsonOutput()
            ));
            long started = System.nanoTime();
            var spec = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON);
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                spec = spec.header("Authorization", "Bearer " + config.getApiKey());
            }
            return AiCalls.timed(
                    spec.bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(this::readContent)
                            .doOnSuccess(text -> TraceLogger.info("AI", "AI_RESPONSE_RECEIVED", "OpenAI-compatible response received",
                                    (System.nanoTime() - started) / 1_000_000,
                                    TraceMeta.of(
                                            "provider", "openai-compatible",
                                            "model", config.getModel(),
                                            "responseLength", text == null ? 0 : text.length()
                                    ))),
                    properties,
                    "openai-compatible",
                    config.getModel(),
                    uri);
        });
    }

    private String readContent(String raw) {
        try {
            JsonNode root = objectMapper().readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "Provider returned an empty response");
            }
            return content.asText();
        } catch (SmartQaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SmartQaException(ErrorCode.AI_PROVIDER_ERROR, "Unable to parse provider response", ex);
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
