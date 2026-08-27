package com.smartqa.clarification;

import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.generation.GenerationRun;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class RuntimeClarificationService {

    public static final String WAITING_FOR_CLARIFICATION = "WAITING_FOR_CLARIFICATION";
    public static final String TARGET_AMBIGUOUS = "TARGET_AMBIGUOUS";

    private final Map<UUID, RuntimeClarification> pending = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<RuntimeClarification>> waits = new ConcurrentHashMap<>();
    private final ProgressEventHub eventHub;

    public RuntimeClarificationService(ObjectProvider<ProgressEventHub> eventHub) {
        this.eventHub = eventHub == null ? null : eventHub.getIfAvailable();
    }

    public RuntimeClarification pause(
            UUID testCaseId,
            UUID executionRunId,
            String stepId,
            String target,
            String reason,
            List<Map<String, Object>> candidates
    ) {
        List<Map<String, Object>> options = candidates == null ? List.of() : List.copyOf(candidates);
        RuntimeClarification clarification = new RuntimeClarification(
                UUID.randomUUID(),
                testCaseId,
                executionRunId,
                stepId,
                target,
                reason == null ? TARGET_AMBIGUOUS : reason,
                "Multiple equally supported candidates. Choose one; SmartQA will not guess.",
                options,
                null,
                WAITING_FOR_CLARIFICATION,
                Instant.now(),
                null
        );
        pending.put(clarification.id(), clarification);
        waits.put(clarification.id(), new CompletableFuture<>());
        emit(clarification);
        return clarification;
    }

    public RuntimeClarification resolve(UUID clarificationId, String selectedCandidateId) {
        RuntimeClarification existing = pending.get(clarificationId);
        if (existing == null) {
            throw new IllegalArgumentException("Clarification not found");
        }
        if (!WAITING_FOR_CLARIFICATION.equals(existing.status())) {
            return existing;
        }
        String selected = selectedCandidateId == null ? "" : selectedCandidateId.trim();
        if (selected.isBlank() || !knownCandidate(existing, selected)) {
            throw new IllegalArgumentException("Selected candidate is not one of the live options");
        }
        RuntimeClarification resolved = existing.resolve(selected);
        pending.put(clarificationId, resolved);
        CompletableFuture<RuntimeClarification> wait = waits.get(clarificationId);
        if (wait != null) {
            wait.complete(resolved);
        }
        emit(resolved);
        return resolved;
    }

    public RuntimeClarification await(UUID clarificationId, Duration timeout) {
        CompletableFuture<RuntimeClarification> wait = waits.get(clarificationId);
        if (wait == null) {
            return pending.get(clarificationId);
        }
        try {
            return wait.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutError) {
            return pending.get(clarificationId);
        } catch (Exception error) {
            Thread.currentThread().interrupt();
            return pending.get(clarificationId);
        }
    }

    public RuntimeClarification get(UUID clarificationId) {
        return pending.get(clarificationId);
    }

    public List<Map<String, Object>> candidatePayloads(
            List<com.smartqa.browser.intelligence.LocatorRanker.RankedElement> ranked,
            String target
    ) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ranked == null) {
            return out;
        }
        int limit = Math.min(4, ranked.size());
        for (int i = 0; i < limit; i++) {
            var rankedElement = ranked.get(i);
            var element = rankedElement.element();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("candidateId", element.candidateId());
            row.put("label", firstNonBlank(element.accessibleName(), element.text(), element.label(), target));
            row.put("role", element.role());
            row.put("tag", element.tag());
            row.put("score", rankedElement.score());
            row.put("context", firstNonBlank(element.headingContext(), element.parentContext(), element.region()));
            out.add(row);
        }
        return out;
    }

    private void emit(RuntimeClarification clarification) {
        if (eventHub == null || clarification.testCaseId() == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("clarificationId", clarification.id().toString());
        details.put("reason", clarification.reason());
        details.put("status", clarification.status());
        details.put("candidates", clarification.candidates());
        details.put("selectedCandidateId", clarification.selectedCandidateId());
        details.put("stepId", clarification.stepId());
        eventHub.emit(
                ProgressEventHub.generationChannel(clarification.testCaseId()),
                ProgressEvent.generation(
                        WAITING_FOR_CLARIFICATION.equals(clarification.status())
                                ? "WAITING_FOR_CLARIFICATION"
                                : "CLARIFICATION_RESOLVED",
                        clarification.question(),
                        clarification.testCaseId(),
                        details
                )
        );
        if (clarification.executionRunId() != null) {
            eventHub.emit(
                    ProgressEventHub.executionChannel(clarification.executionRunId()),
                    ProgressEvent.execution(
                            WAITING_FOR_CLARIFICATION.equals(clarification.status())
                                    ? "WAITING_FOR_CLARIFICATION"
                                    : "CLARIFICATION_RESOLVED",
                            clarification.question(),
                            clarification.testCaseId(),
                            clarification.executionRunId(),
                            details
                    )
            );
        }
    }

    private static boolean knownCandidate(RuntimeClarification clarification, String selected) {
        for (Map<String, Object> candidate : clarification.candidates()) {
            Object id = candidate.get("candidateId");
            Object label = candidate.get("label");
            if (selected.equals(String.valueOf(id)) || selected.equals(String.valueOf(label))) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public record RuntimeClarification(
            UUID id,
            UUID testCaseId,
            UUID executionRunId,
            String stepId,
            String target,
            String reason,
            String question,
            List<Map<String, Object>> candidates,
            String selectedCandidateId,
            String status,
            Instant createdAt,
            Instant resolvedAt
    ) {
        public RuntimeClarification {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        public RuntimeClarification resolve(String selectedCandidateId) {
            return new RuntimeClarification(
                    id, testCaseId, executionRunId, stepId, target, reason, question, candidates,
                    selectedCandidateId, "RESOLVED", createdAt, Instant.now());
        }
    }
}
