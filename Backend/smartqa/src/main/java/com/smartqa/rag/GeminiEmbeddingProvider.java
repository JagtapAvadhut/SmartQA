package com.smartqa.rag;

import com.smartqa.ai.AiCalls;
import com.smartqa.ai.GeminiKeyPool;
import com.smartqa.common.config.SmartQaProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini embedding models (e.g. text-embedding-004). Uses the shared {@link GeminiKeyPool}.
 */
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SmartQaProperties properties;
    private final GeminiKeyPool keyPool;

    public GeminiEmbeddingProvider(WebClient webClient, ObjectMapper objectMapper, SmartQaProperties properties) {
        this(webClient, objectMapper, properties, new GeminiKeyPool(properties));
    }

    public GeminiEmbeddingProvider(
            WebClient webClient,
            ObjectMapper objectMapper,
            SmartQaProperties properties,
            GeminiKeyPool keyPool) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.keyPool = keyPool == null ? new GeminiKeyPool(properties) : keyPool;
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public String model() {
        return properties.getRag().getGeminiEmbeddingModel();
    }

    @Override
    public int dimension() {
        return properties.getRag().getEmbeddingDimension();
    }

    @Override
    public Mono<Boolean> available() {
        return Mono.just(keyPool.hasKeys());
    }

    @Override
    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.error(new IllegalArgumentException("embed text must be non-blank"));
        }
        String base = trimSlash(properties.getAi().getGemini().getBaseUrl());
        String uri = base + "/v1beta/models/" + model() + ":embedContent";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "models/" + model());
        body.put("content", Map.of("parts", List.of(Map.of(
                "text", text.length() > 8000 ? text.substring(0, 8000) : text))));
        return keyPool.execute("embed", model(), lease -> webClient.post()
                .uri(uri)
                .header("x-goog-api-key", lease.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("gemini-embed"))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(Math.max(30, properties.getAi().getTimeoutSeconds())))
                .map(this::parseEmbedding));
    }

    private float[] parseEmbedding(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode values = root.path("embedding").path("values");
            if (!values.isArray() || values.size() == 0) {
                throw new IllegalStateException("gemini embedding empty");
            }
            float[] out = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                out[i] = (float) values.get(i).asDouble();
            }
            int expected = dimension();
            if (expected > 0 && out.length != expected) {
                throw new IllegalStateException(
                        "embedding dimension mismatch: got=" + out.length + " expected=" + expected + " model=" + model());
            }
            return out;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse gemini embedding: " + ex.getMessage(), ex);
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
