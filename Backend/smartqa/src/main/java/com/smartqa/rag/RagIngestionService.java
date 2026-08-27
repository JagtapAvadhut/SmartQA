package com.smartqa.rag;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * knowledge → sanitize → normalize → embed → pgvector insert
 */
@Service
public class RagIngestionService {

    private final EmbeddingProvider embeddingProvider;
    private final RagKnowledgeRepository repository;
    private final SmartQaProperties properties;

    public RagIngestionService(
            EmbeddingProvider embeddingProvider,
            RagKnowledgeRepository repository,
            SmartQaProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.properties = properties;
    }

    public Mono<KnowledgeDocument> ingest(
            KnowledgeScope scope,
            String scopeKey,
            KnowledgeContentType contentType,
            String content,
            String source,
            UUID sourceRunId,
            UUID sourceTestCaseId) {
        if (!properties.getRag().isEnabled()) {
            return Mono.empty();
        }
        String sanitized = KnowledgeSanitizer.sanitize(content);
        if (sanitized.isBlank() || KnowledgeSanitizer.looksSecretHeavy(content)) {
            TraceLogger.warn("RAG", "INGEST_REJECTED_SECRET", "Rejected secret-heavy content", TraceMeta.of(
                    "scope", scope == null ? "" : scope.name(),
                    "contentType", contentType == null ? "" : contentType.name()
            ));
            return Mono.empty();
        }
        String normalized = normalize(sanitized);
        String key = normalizeScopeKey(scope, scopeKey);
        long started = System.currentTimeMillis();
        return repository.existsSimilarContent(scope, key, normalized)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        TraceLogger.info("RAG", "INGEST_SKIP_DUPLICATE", "Similar content already stored", TraceMeta.of(
                                "scope", scope.name(),
                                "scopeKey", key
                        ));
                        return Mono.empty();
                    }
                    return embeddingProvider.embed(normalized)
                            .flatMap(embedding -> {
                                KnowledgeDocument doc = new KnowledgeDocument();
                                doc.setId(UUID.randomUUID());
                                doc.setScope(scope);
                                doc.setScopeKey(key);
                                doc.setContent(normalized);
                                doc.setContentType(contentType);
                                doc.setSource(source == null ? "system" : source);
                                doc.setSourceRunId(sourceRunId);
                                doc.setSourceTestCaseId(sourceTestCaseId);
                                doc.setMetadataJson("{}");
                                doc.setConfidence(0.55);
                                doc.setSuccessCount(0);
                                doc.setFailureCount(0);
                                doc.setEmbedding(embedding);
                                doc.setCreatedAt(LocalDateTime.now());
                                doc.setUpdatedAt(LocalDateTime.now());
                                return repository.insert(doc);
                            })
                            .doOnNext(saved -> TraceLogger.info("RAG", "INGEST_OK", "Knowledge ingested", TraceMeta.of(
                                    "id", saved.getId() == null ? "" : saved.getId().toString(),
                                    "scope", scope.name(),
                                    "contentType", contentType.name(),
                                    "provider", embeddingProvider.id(),
                                    "model", embeddingProvider.model(),
                                    "dimension", embeddingProvider.dimension(),
                                    "latencyMs", System.currentTimeMillis() - started
                            )));
                })
                .onErrorResume(err -> {
                    TraceLogger.warn("RAG", "INGEST_FAILED", "RAG ingest failed", TraceMeta.of(
                            "message", err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage()
                    ));
                    return Mono.empty();
                });
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeScopeKey(KnowledgeScope scope, String scopeKey) {
        if (scope == KnowledgeScope.GLOBAL_GENERIC) {
            return "global";
        }
        if (scopeKey == null || scopeKey.isBlank()) {
            return scope == KnowledgeScope.EXECUTION ? "execution" : "unknown";
        }
        return scopeKey.trim().toLowerCase(Locale.ROOT);
    }
}
