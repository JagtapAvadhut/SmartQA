package com.smartqa.rag;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reactive pgvector access via DatabaseClient (no JPA, no blocking JDBC).
 * Embeddings are bound as text literals and cast to vector in SQL.
 */
@Repository
public class RagKnowledgeRepository {

    private final DatabaseClient databaseClient;

    public RagKnowledgeRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<KnowledgeDocument> insert(KnowledgeDocument doc) {
        String vector = VectorLiteral.toLiteral(doc.getEmbedding());
        LocalDateTime now = LocalDateTime.now();
        UUID id = doc.getId() == null ? UUID.randomUUID() : doc.getId();
        var spec = databaseClient.sql("""
                        INSERT INTO smartqa_knowledge (
                            id, scope, scope_key, content, content_type, source,
                            source_run_id, source_test_case_id, metadata_json,
                            confidence, success_count, failure_count, last_used_at,
                            embedding, created_at, updated_at
                        ) VALUES (
                            :id, :scope, :scopeKey, :content, :contentType, :source,
                            :sourceRunId, :sourceTestCaseId, :metadataJson,
                            :confidence, :successCount, :failureCount, :lastUsedAt,
                            CAST(:embedding AS vector), :createdAt, :updatedAt
                        )
                        """)
                .bind("id", id)
                .bind("scope", doc.getScope().name())
                .bind("scopeKey", nullToEmpty(doc.getScopeKey()))
                .bind("content", doc.getContent())
                .bind("contentType", doc.getContentType().name())
                .bind("source", nullToEmpty(doc.getSource()));
        spec = bindUuid(spec, "sourceRunId", doc.getSourceRunId());
        spec = bindUuid(spec, "sourceTestCaseId", doc.getSourceTestCaseId());
        spec = spec.bind("metadataJson", doc.getMetadataJson() == null ? "{}" : doc.getMetadataJson())
                .bind("confidence", doc.getConfidence())
                .bind("successCount", doc.getSuccessCount())
                .bind("failureCount", doc.getFailureCount());
        if (doc.getLastUsedAt() == null) {
            spec = spec.bindNull("lastUsedAt", LocalDateTime.class);
        } else {
            spec = spec.bind("lastUsedAt", doc.getLastUsedAt());
        }
        return spec.bind("embedding", vector)
                .bind("createdAt", doc.getCreatedAt() == null ? now : doc.getCreatedAt())
                .bind("updatedAt", now)
                .fetch()
                .rowsUpdated()
                .then(Mono.fromCallable(() -> {
                    doc.setId(id);
                    doc.setUpdatedAt(now);
                    if (doc.getCreatedAt() == null) {
                        doc.setCreatedAt(now);
                    }
                    return doc;
                }));
    }

    private static DatabaseClient.GenericExecuteSpec bindUuid(
            DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
        if (value == null) {
            return spec.bindNull(name, UUID.class);
        }
        return spec.bind(name, value);
    }

    /**
     * Vector similarity search with optional scope filters.
     * Uses cosine distance (<=>); similarity = 1 - distance.
     */
    public Flux<KnowledgeDocument> searchSimilar(
            float[] queryEmbedding,
            List<KnowledgeScope> scopes,
            String applicationScopeKey,
            int limit) {
        String vector = VectorLiteral.toLiteral(queryEmbedding);
        int top = Math.max(1, Math.min(limit, 50));
        StringBuilder sql = new StringBuilder("""
                SELECT id, scope, scope_key, content, content_type, source,
                       source_run_id, source_test_case_id, metadata_json,
                       confidence, success_count, failure_count, last_used_at,
                       created_at, updated_at,
                       (1 - (embedding <=> CAST(:embedding AS vector))) AS similarity
                FROM smartqa_knowledge
                WHERE 1=1
                """);
        List<KnowledgeScope> scopeList = scopes == null || scopes.isEmpty()
                ? List.of(KnowledgeScope.GLOBAL_GENERIC, KnowledgeScope.APPLICATION, KnowledgeScope.EXECUTION)
                : scopes;
        sql.append(" AND scope IN (");
        for (int i = 0; i < scopeList.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append("'").append(scopeList.get(i).name()).append("'");
        }
        sql.append(")");
        if (applicationScopeKey != null && !applicationScopeKey.isBlank()) {
            sql.append(" AND (scope <> 'APPLICATION' OR scope_key = :appKey)");
        } else {
            sql.append(" AND scope <> 'APPLICATION'");
        }
        sql.append("""
                 ORDER BY embedding <=> CAST(:embedding AS vector) ASC,
                          (success_count::float / GREATEST(success_count + failure_count, 1)) DESC,
                          updated_at DESC
                 LIMIT :limit
                """);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
                .bind("embedding", vector)
                .bind("limit", top);
        if (applicationScopeKey != null && !applicationScopeKey.isBlank()) {
            spec = spec.bind("appKey", applicationScopeKey.toLowerCase(Locale.ROOT));
        }
        return spec.map((row, meta) -> mapRow(row)).all();
    }

    public Mono<Long> countAll() {
        return databaseClient.sql("SELECT COUNT(*) AS c FROM smartqa_knowledge")
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    public Mono<Void> markUsed(UUID id, boolean success) {
        String sql = success
                ? """
                  UPDATE smartqa_knowledge
                  SET success_count = success_count + 1,
                      last_used_at = :now,
                      updated_at = :now,
                      confidence = LEAST(0.99, confidence + 0.02)
                  WHERE id = :id
                  """
                : """
                  UPDATE smartqa_knowledge
                  SET failure_count = failure_count + 1,
                      last_used_at = :now,
                      updated_at = :now,
                      confidence = GREATEST(0.05, confidence - 0.03)
                  WHERE id = :id
                  """;
        return databaseClient.sql(sql)
                .bind("id", id)
                .bind("now", LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Boolean> existsSimilarContent(KnowledgeScope scope, String scopeKey, String contentPrefix) {
        String prefix = contentPrefix == null ? "" : contentPrefix;
        if (prefix.length() > 80) {
            prefix = prefix.substring(0, 80);
        }
        return databaseClient.sql("""
                        SELECT COUNT(*) AS c FROM smartqa_knowledge
                        WHERE scope = :scope
                          AND COALESCE(scope_key, '') = :scopeKey
                          AND content LIKE :prefix
                        """)
                .bind("scope", scope.name())
                .bind("scopeKey", nullToEmpty(scopeKey))
                .bind("prefix", prefix + "%")
                .map((row, meta) -> {
                    Long c = row.get("c", Long.class);
                    return c != null && c > 0;
                })
                .one()
                .defaultIfEmpty(false);
    }

    private static KnowledgeDocument mapRow(io.r2dbc.spi.Readable row) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(row.get("id", UUID.class));
        doc.setScope(KnowledgeScope.parse(row.get("scope", String.class)));
        doc.setScopeKey(row.get("scope_key", String.class));
        doc.setContent(row.get("content", String.class));
        doc.setContentType(KnowledgeContentType.parse(row.get("content_type", String.class)));
        doc.setSource(row.get("source", String.class));
        doc.setSourceRunId(row.get("source_run_id", UUID.class));
        doc.setSourceTestCaseId(row.get("source_test_case_id", UUID.class));
        doc.setMetadataJson(row.get("metadata_json", String.class));
        Double conf = row.get("confidence", Double.class);
        doc.setConfidence(conf == null ? 0.5 : conf);
        Integer sc = row.get("success_count", Integer.class);
        doc.setSuccessCount(sc == null ? 0 : sc);
        Integer fc = row.get("failure_count", Integer.class);
        doc.setFailureCount(fc == null ? 0 : fc);
        doc.setLastUsedAt(row.get("last_used_at", LocalDateTime.class));
        doc.setCreatedAt(row.get("created_at", LocalDateTime.class));
        doc.setUpdatedAt(row.get("updated_at", LocalDateTime.class));
        Double sim = row.get("similarity", Double.class);
        doc.setSimilarity(sim);
        return doc;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
