package com.smartqa.rag;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal internal RAG status (not a public UI surface).
 */
@RestController
@RequestMapping("/api/internal/rag")
public class RagStatusController {

    private final EmbeddingProvider embeddingProvider;
    private final RagKnowledgeRepository repository;
    private final DatabaseClient databaseClient;

    public RagStatusController(
            EmbeddingProvider embeddingProvider,
            RagKnowledgeRepository repository,
            DatabaseClient databaseClient) {
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.databaseClient = databaseClient;
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> stats() {
        Mono<Long> count = repository.countAll().defaultIfEmpty(0L);
        Mono<String> ext = databaseClient.sql(
                        "SELECT COALESCE((SELECT extversion FROM pg_extension WHERE extname='vector'), 'missing') AS v")
                .map((row, meta) -> row.get("v", String.class))
                .one()
                .defaultIfEmpty("missing");
        Mono<Boolean> available = embeddingProvider.available().defaultIfEmpty(false);
        return Mono.zip(count, ext, available)
                .map(tuple -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("pgvector", "missing".equals(tuple.getT2()) ? "BLOCKED" : "AVAILABLE");
                    out.put("extensionVersion", tuple.getT2());
                    out.put("embeddingProvider", embeddingProvider.id());
                    out.put("embeddingModel", embeddingProvider.model());
                    out.put("embeddingDimension", embeddingProvider.dimension());
                    out.put("embeddingAvailable", tuple.getT3());
                    out.put("knowledgeCount", tuple.getT1());
                    out.put("vectorTable", "smartqa_knowledge");
                    out.put("vectorIndex", "idx_smartqa_knowledge_embedding_hnsw");
                    out.put("distanceMetric", "cosine");
                    return out;
                });
    }
}
