package com.smartqa.rag;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Selects embedding provider: preferred → ollama → gemini.
 * Never uses a chat-generation model as an embedding model.
 */
public class FallbackEmbeddingProvider implements EmbeddingProvider {

    private final OllamaEmbeddingProvider ollama;
    private final GeminiEmbeddingProvider gemini;
    private final SmartQaProperties properties;

    public FallbackEmbeddingProvider(
            OllamaEmbeddingProvider ollama,
            GeminiEmbeddingProvider gemini,
            SmartQaProperties properties) {
        this.ollama = ollama;
        this.gemini = gemini;
        this.properties = properties;
    }

    @Override
    public String id() {
        return resolve().id();
    }

    @Override
    public String model() {
        return resolve().model();
    }

    @Override
    public int dimension() {
        return properties.getRag().getEmbeddingDimension();
    }

    @Override
    public Mono<Boolean> available() {
        return resolve().available();
    }

    @Override
    public Mono<float[]> embed(String text) {
        EmbeddingProvider primary = resolve();
        return primary.embed(text)
                .doOnError(err -> TraceLogger.warn("RAG", "EMBEDDING_PRIMARY_FAILED", "Primary embedding failed", TraceMeta.of(
                        "provider", primary.id(),
                        "model", primary.model(),
                        "message", err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage()
                )))
                .onErrorResume(err -> {
                    EmbeddingProvider secondary = secondaryOf(primary);
                    if (secondary == null) {
                        return Mono.error(err);
                    }
                    return secondary.available().flatMap(ok -> {
                        if (!Boolean.TRUE.equals(ok)) {
                            return Mono.error(err);
                        }
                        TraceLogger.info("RAG", "EMBEDDING_FALLBACK", "Using secondary embedding provider", TraceMeta.of(
                                "from", primary.id(),
                                "to", secondary.id(),
                                "model", secondary.model()
                        ));
                        return secondary.embed(text);
                    });
                });
    }

    private EmbeddingProvider resolve() {
        String preferred = properties.getRag().getEmbeddingProvider();
        String id = preferred == null ? "ollama" : preferred.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "gemini" -> gemini;
            default -> ollama;
        };
    }

    private EmbeddingProvider secondaryOf(EmbeddingProvider primary) {
        if ("ollama".equals(primary.id())) {
            return gemini;
        }
        if ("gemini".equals(primary.id())) {
            return ollama;
        }
        return null;
    }
}
