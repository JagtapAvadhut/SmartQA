package com.smartqa.rag;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compact reusable knowledge row for vector RAG.
 * Embedding is stored as pgvector; application code holds float[] transiently.
 */
public class KnowledgeDocument {

    private UUID id;
    private KnowledgeScope scope;
    private String scopeKey;
    private String content;
    private KnowledgeContentType contentType;
    private String source;
    private UUID sourceRunId;
    private UUID sourceTestCaseId;
    private String metadataJson;
    private double confidence;
    private int successCount;
    private int failureCount;
    private LocalDateTime lastUsedAt;
    private float[] embedding;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Cosine similarity in [0,1] when retrieved (1 - distance). */
    private Double similarity;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public KnowledgeScope getScope() { return scope; }
    public void setScope(KnowledgeScope scope) { this.scope = scope; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public KnowledgeContentType getContentType() { return contentType; }
    public void setContentType(KnowledgeContentType contentType) { this.contentType = contentType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public UUID getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(UUID sourceRunId) { this.sourceRunId = sourceRunId; }
    public UUID getSourceTestCaseId() { return sourceTestCaseId; }
    public void setSourceTestCaseId(UUID sourceTestCaseId) { this.sourceTestCaseId = sourceTestCaseId; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double similarity) { this.similarity = similarity; }
}
