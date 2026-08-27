package com.smartqa.rag;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.pipeline.FailureEvidence;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Semantic retrieval with relevance gate. Live DOM always outranks RAG.
 */
@Service
public class RagRetrievalService {

    private final EmbeddingProvider embeddingProvider;
    private final RagKnowledgeRepository repository;
    private final SmartQaProperties properties;

    public RagRetrievalService(
            EmbeddingProvider embeddingProvider,
            RagKnowledgeRepository repository,
            SmartQaProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.properties = properties;
    }

    public Mono<RagRetrievalResult> retrieve(RagRetrievalRequest request) {
        if (!properties.getRag().isEnabled()) {
            return Mono.just(RagRetrievalResult.empty("rag_disabled", "", "", 0));
        }
        String query = request.query() == null ? "" : request.query().trim();
        if (query.isBlank()) {
            return Mono.just(RagRetrievalResult.empty("empty_query", embeddingProvider.id(), embeddingProvider.model(), 0));
        }
        String querySummary = query.length() > 160 ? query.substring(0, 160) : query;
        int topK = request.topK() > 0 ? request.topK() : properties.getRag().getTopK();
        double threshold = properties.getRag().getRelevanceThreshold();
        long started = System.currentTimeMillis();

        return embeddingProvider.embed(query)
                .flatMapMany(embedding -> repository.searchSimilar(
                        embedding,
                        List.of(KnowledgeScope.GLOBAL_GENERIC, KnowledgeScope.APPLICATION, KnowledgeScope.EXECUTION),
                        request.applicationKey(),
                        Math.max(topK * 3, 15)))
                .collectList()
                .map(candidates -> {
                    List<KnowledgeDocument> accepted = new ArrayList<>();
                    List<KnowledgeDocument> rejected = new ArrayList<>();
                    List<KnowledgeDocument> ranked = rerank(candidates, request);
                    for (KnowledgeDocument doc : ranked) {
                        double score = doc.getSimilarity() == null ? 0.0 : doc.getSimilarity();
                        if (score >= threshold && accepted.size() < topK) {
                            accepted.add(doc);
                        } else {
                            rejected.add(doc);
                        }
                    }
                    double top = accepted.isEmpty()
                            ? ranked.stream().mapToDouble(d -> d.getSimilarity() == null ? 0 : d.getSimilarity()).max().orElse(0)
                            : accepted.getFirst().getSimilarity() == null ? 0 : accepted.getFirst().getSimilarity();
                    long latency = System.currentTimeMillis() - started;
                    TraceLogger.info("RAG", "RETRIEVAL", "Vector RAG retrieval", TraceMeta.of(
                            "reason", "failure_diagnosis",
                            "scope", summarizeScopes(accepted),
                            "querySummary", querySummary,
                            "topK", topK,
                            "retrievedCount", ranked.size(),
                            "topSimilarity", top,
                            "acceptedCount", accepted.size(),
                            "rejectedCount", rejected.size(),
                            "latencyMs", latency,
                            "embeddingProvider", embeddingProvider.id(),
                            "embeddingModel", embeddingProvider.model()
                    ));
                    return new RagRetrievalResult(
                            List.copyOf(accepted),
                            List.copyOf(rejected),
                            ranked.size(),
                            top,
                            latency,
                            embeddingProvider.id(),
                            embeddingProvider.model(),
                            querySummary);
                })
                .onErrorResume(err -> {
                    long latency = System.currentTimeMillis() - started;
                    TraceLogger.warn("RAG", "RETRIEVAL_FAILED", "RAG retrieval failed", TraceMeta.of(
                            "message", err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage(),
                            "latencyMs", latency
                    ));
                    return Mono.just(RagRetrievalResult.empty(querySummary, embeddingProvider.id(), embeddingProvider.model(), latency));
                });
    }

    public Mono<RagRetrievalResult> retrieveForFailure(FailureEvidence evidence) {
        String query = buildFailureQuery(evidence);
        String appKey = applicationKeyFrom(evidence == null ? null : evidence.url());
        return retrieve(RagRetrievalRequest.builder()
                .query(query)
                .applicationKey(appKey)
                .failureCategory(evidence == null ? null : evidence.failureCategory())
                .topK(properties.getRag().getTopK())
                .build());
    }

    static String buildFailureQuery(FailureEvidence evidence) {
        if (evidence == null) {
            return "generic browser recovery";
        }
        String base = String.join(" | ",
                safe(evidence.failureCategory()),
                safe(evidence.action()),
                safe(evidence.target()),
                safe(evidence.instruction()),
                safe(evidence.exception()),
                safe(evidence.expected()),
                safe(evidence.actual())
        ).trim();
        String category = safe(evidence.failureCategory()).toLowerCase(java.util.Locale.ROOT);
        if (category.contains("search") || category.contains("filter") || category.contains("tab")
                || category.contains("state") || category.contains("select")) {
            return (base + " | search verification autocomplete filter ownership native select normalization new-tab detection state verification").trim();
        }
        return base;
    }

    static String applicationKeyFrom(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception ex) {
            return null;
        }
    }

    private List<KnowledgeDocument> rerank(List<KnowledgeDocument> candidates, RagRetrievalRequest request) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String category = request.failureCategory() == null ? "" : request.failureCategory().toLowerCase(Locale.ROOT);
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((KnowledgeDocument d) -> scopeBoost(d, request) + typeBoost(d, category) + successBoost(d))
                        .reversed()
                        .thenComparing(d -> d.getSimilarity() == null ? 0.0 : d.getSimilarity(), Comparator.reverseOrder()))
                .toList();
    }

    private static double scopeBoost(KnowledgeDocument d, RagRetrievalRequest request) {
        if (d.getScope() == KnowledgeScope.APPLICATION
                && request.applicationKey() != null
                && request.applicationKey().equalsIgnoreCase(nullToEmpty(d.getScopeKey()))) {
            return 0.15;
        }
        if (d.getScope() == KnowledgeScope.GLOBAL_GENERIC) {
            return 0.05;
        }
        if (d.getScope() == KnowledgeScope.EXECUTION) {
            return 0.02;
        }
        return 0.0;
    }

    private static double typeBoost(KnowledgeDocument d, String category) {
        if (category.isBlank() || d.getContentType() == null) {
            return 0.0;
        }
        String type = d.getContentType().name().toLowerCase(Locale.ROOT);
        if (category.contains("filter") && type.contains("filter")) {
            return 0.12;
        }
        if (category.contains("search") && type.contains("search")) {
            return 0.12;
        }
        if (category.contains("assert") && type.contains("assertion")) {
            return 0.12;
        }
        if ((category.contains("overlay") || category.contains("action")) && type.contains("recovery")) {
            return 0.1;
        }
        if (category.contains("tab") && type.contains("tab")) {
            return 0.1;
        }
        return 0.0;
    }

    private static double successBoost(KnowledgeDocument d) {
        int total = d.getSuccessCount() + d.getFailureCount();
        if (total <= 0) {
            return 0.0;
        }
        return 0.1 * ((double) d.getSuccessCount() / total);
    }

    private static String summarizeScopes(List<KnowledgeDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return "none";
        }
        return docs.stream().map(d -> d.getScope().name()).distinct().reduce((a, b) -> a + "," + b).orElse("none");
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
