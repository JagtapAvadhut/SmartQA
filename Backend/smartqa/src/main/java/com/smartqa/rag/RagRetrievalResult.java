package com.smartqa.rag;

import java.util.List;

public record RagRetrievalResult(
        List<KnowledgeDocument> accepted,
        List<KnowledgeDocument> rejected,
        int retrievedCount,
        double topScore,
        long latencyMs,
        String embeddingProvider,
        String embeddingModel,
        String querySummary
) {
    public static RagRetrievalResult empty(String querySummary, String provider, String model, long latencyMs) {
        return new RagRetrievalResult(List.of(), List.of(), 0, 0.0, latencyMs, provider, model, querySummary);
    }

    public String toAdvisoryPromptBlock() {
        if (accepted == null || accepted.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Vector RAG memories (advisory only — live DOM outranks; never copy selectors blindly):\n");
        for (KnowledgeDocument doc : accepted) {
            sb.append("- [").append(doc.getScope()).append('/').append(doc.getContentType()).append(']');
            if (doc.getSimilarity() != null) {
                sb.append(" sim=").append(String.format("%.3f", doc.getSimilarity()));
            }
            sb.append(' ').append(doc.getContent()).append('\n');
        }
        sb.append("Priority: CURRENT LIVE DOM > CURRENT EXECUTION EVIDENCE > APPLICATION RAG > GLOBAL GENERIC RAG > LLM prior.\n");
        return sb.toString();
    }
}
