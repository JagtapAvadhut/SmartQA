package com.smartqa.rag;

import com.smartqa.pipeline.AiDiagnosticResult;
import com.smartqa.pipeline.FailureEvidence;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.UUID;

/**
 * After successful recovery, store a compact generic pattern (not site-specific selectors).
 */
@Service
public class RagFeedbackService {

    private final RagIngestionService ingestionService;
    private final RagKnowledgeRepository repository;

    public RagFeedbackService(RagIngestionService ingestionService, RagKnowledgeRepository repository) {
        this.ingestionService = ingestionService;
        this.repository = repository;
    }

    public Mono<Void> rememberSuccessfulRecovery(
            AiDiagnosticResult result,
            FailureEvidence evidence,
            RagRetrievalResult retrieval) {
        if (result == null) {
            return Mono.empty();
        }
        Mono<Void> markUsed = Mono.empty();
        if (retrieval != null && retrieval.accepted() != null) {
            markUsed = reactor.core.publisher.Flux.fromIterable(retrieval.accepted())
                    .concatMap(doc -> repository.markUsed(doc.getId(), true))
                    .then();
        }
        String explanation = result.explanation();
        if (explanation == null || explanation.isBlank() || KnowledgeSanitizer.looksSecretHeavy(explanation)) {
            return markUsed;
        }
        KnowledgeContentType type = inferType(result, evidence);
        String content = "Failure " + safe(result.normalizedClassification())
                + ": " + KnowledgeSanitizer.sanitize(explanation)
                + " Prefer live DOM verification before applying recovery.";
        UUID runId = parseUuid(evidence == null ? null : evidence.runId());
        UUID testCaseId = parseUuid(evidence == null ? null : evidence.testCaseId());
        return markUsed.then(ingestionService.ingest(
                        KnowledgeScope.GLOBAL_GENERIC,
                        "global",
                        type,
                        content,
                        "recovery_feedback",
                        runId,
                        testCaseId)
                .then());
    }

    private static KnowledgeContentType inferType(AiDiagnosticResult result, FailureEvidence evidence) {
        String blob = (safe(result.normalizedClassification()) + " "
                + safe(evidence == null ? null : evidence.failureCategory())).toLowerCase(Locale.ROOT);
        if (blob.contains("filter")) {
            return KnowledgeContentType.FILTER_PATTERN;
        }
        if (blob.contains("search")) {
            return KnowledgeContentType.SEARCH_PATTERN;
        }
        if (blob.contains("assert")) {
            return KnowledgeContentType.ASSERTION_PATTERN;
        }
        if (blob.contains("tab")) {
            return KnowledgeContentType.TAB_PATTERN;
        }
        if (blob.contains("locator") || blob.contains("ambiguous")) {
            return KnowledgeContentType.LOCATOR_PATTERN;
        }
        return KnowledgeContentType.RECOVERY_PATTERN;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
