package com.smartqa.rag;

import com.smartqa.ai.AiCalls;
import com.smartqa.common.config.SmartQaProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local Ollama embeddings via POST /api/embeddings.
 * Default model: nomic-embed-text (768-d). Not a chat model.
 */
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SmartQaProperties properties;

    public OllamaEmbeddingProvider(WebClient webClient, ObjectMapper objectMapper, SmartQaProperties properties) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public String model() {
        return properties.getRag().getOllamaEmbeddingModel();
    }

    @Override
    public int dimension() {
        return properties.getRag().getEmbeddingDimension();
    }

    @Override
    public Mono<Boolean> available() {
        String baseUrl = trimSlash(properties.getAi().getOllama().getBaseUrl());
        String model = model();
        return webClient.get()
                .uri(baseUrl + "/api/tags")
                .retrieve()
                .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("ollama-embed"))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(Math.max(5, properties.getAi().getConnectTimeoutSeconds())))
                .map(raw -> raw != null && raw.toLowerCase().contains(model.split(":")[0].toLowerCase()))
                .onErrorReturn(false);
    }

    @Override
    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.error(new IllegalArgumentException("embed text must be non-blank"));
        }
        String baseUrl = trimSlash(properties.getAi().getOllama().getBaseUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("prompt", text.length() > 8000 ? text.substring(0, 8000) : text);
        long started = System.nanoTime();
        return webClient.post()
                .uri(baseUrl + "/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, AiCalls.errorStatus("ollama-embed"))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(Math.max(30, properties.getAi().getTimeoutSeconds())))
                .map(raw -> parseEmbedding(raw, started));
    }

    private float[] parseEmbedding(String raw, long startedNanos) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode emb = root.path("embedding");
            if (!emb.isArray() || emb.size() == 0) {
                throw new IllegalStateException("ollama embedding empty");
            }
            float[] out = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                out[i] = (float) emb.get(i).asDouble();
            }
            int expected = dimension();
            if (expected > 0 && out.length != expected) {
                throw new IllegalStateException(
                        "embedding dimension mismatch: got=" + out.length + " expected=" + expected
                                + " model=" + model() + " latencyMs=" + ((System.nanoTime() - startedNanos) / 1_000_000));
            }
            return out;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse ollama embedding: " + ex.getMessage(), ex);
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
