package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Structured AI output only. Never Playwright code, unknown selectors, or click coordinates.
 */
public record TargetHypothesis(
        String classification,
        String semanticField,
        String targetValue,
        String recommendedCandidateId,
        String recommendedStrategy,
        double confidence,
        boolean visualTargetPresent,
        boolean domResolved,
        String reason,
        List<String> candidateEvidence,
        String targetType,
        String visibleText,
        VisualRegion visualRegion,
        String decision,
        String operation,
        String controlId,
        String containerId,
        List<String> evidenceUsed
) {
    public TargetHypothesis {
        candidateEvidence = candidateEvidence == null ? List.of() : List.copyOf(candidateEvidence);
        evidenceUsed = evidenceUsed == null ? List.of() : List.copyOf(evidenceUsed);
    }

    public TargetHypothesis(
            String classification,
            String semanticField,
            String targetValue,
            String recommendedCandidateId,
            String recommendedStrategy,
            double confidence,
            boolean visualTargetPresent,
            boolean domResolved,
            String reason,
            List<String> candidateEvidence) {
        this(classification, semanticField, targetValue, recommendedCandidateId, recommendedStrategy,
                confidence, visualTargetPresent, domResolved, reason, candidateEvidence, null, null, null,
                null, null, null, null, List.of());
    }

    public TargetHypothesis(
            String classification,
            String semanticField,
            String targetValue,
            String recommendedCandidateId,
            String recommendedStrategy,
            double confidence,
            boolean visualTargetPresent,
            boolean domResolved,
            String reason,
            List<String> candidateEvidence,
            String targetType,
            String visibleText,
            VisualRegion visualRegion
    ) {
        this(classification, semanticField, targetValue, recommendedCandidateId, recommendedStrategy,
                confidence, visualTargetPresent, domResolved, reason, candidateEvidence, targetType, visibleText,
                visualRegion, null, null, null, null, List.of());
    }
    public static TargetHypothesis absent(String reason) {
        return new TargetHypothesis(
                "TARGET_NOT_PRESENT",
                null,
                null,
                null,
                "stop_and_report",
                0.4,
                false,
                false,
                reason,
                List.of());
    }

    public static TargetHypothesis aiUnavailable(String reason) {
        return new TargetHypothesis(
                "AI_UNAVAILABLE",
                null,
                null,
                null,
                "continue_deterministic",
                0.0,
                false,
                false,
                reason == null || reason.isBlank() ? "AI unavailable" : reason,
                List.of());
    }

    public static TargetHypothesis visualUnresolved(String reason) {
        return new TargetHypothesis(
                "VISUAL_TARGET_PRESENT_DOM_UNRESOLVED",
                null,
                null,
                null,
                "rediscover_shadow_iframe_stale",
                0.55,
                true,
                false,
                reason,
                List.of());
    }

    public boolean isSafeToAttempt() {
        if (decision != null && decision.toUpperCase(Locale.ROOT).startsWith("REJECT")) {
            return false;
        }
        String candidateId = firstNonBlank(recommendedCandidateId, controlId);
        return candidateId != null && !candidateId.isBlank()
                && !forbiddenStrategy(recommendedStrategy)
                && confidence >= 0.45
                && !"TARGET_NOT_PRESENT".equalsIgnoreCase(classification);
    }

    public String liveCandidateId() {
        return firstNonBlank(recommendedCandidateId, controlId);
    }

    public static TargetHypothesis fromLiveCandidate(ElementCandidate live) {
        if (live == null) {
            return absent("missing live candidate");
        }
        String heading = live.headingContext();
        String name = firstNonBlank(live.accessibleName(), live.text());
        return new TargetHypothesis(
                "GENERIC_TARGET",
                heading,
                name,
                live.candidateId(),
                "resolve_existing_candidate",
                0.9,
                false,
                true,
                "Selected from live inventory",
                List.of(live.candidateId()),
                null,
                live.text(),
                null,
                "USE_EXISTING_CANDIDATE",
                null,
                live.candidateId(),
                live.containerId(),
                List.of("live_inventory")
        );
    }

    public static boolean forbiddenStrategy(String strategy) {
        if (strategy == null) {
            return false;
        }
        String s = strategy.toLowerCase(Locale.ROOT);
        return s.contains("playwright") || s.contains("evaluate") || s.contains("xpath")
                || s.contains("coordinate") || s.contains("nth(") || s.contains("css=");
    }

    public static TargetHypothesis fromJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return absent("empty AI hypothesis");
        }
        List<String> evidence = new ArrayList<>();
        JsonNode ev = node.path("candidateEvidence");
        if (ev.isArray()) {
            for (JsonNode item : ev) {
                if (item.isTextual()) {
                    evidence.add(item.asText());
                } else {
                    String desc = item.path("description").asText("");
                    String reason = item.path("reason").asText("");
                    if (!desc.isBlank() || !reason.isBlank()) {
                        evidence.add((desc + " " + reason).trim());
                    }
                }
            }
        }
        String candidateId = firstText(node, "", "recommendedCandidateId", "candidateId", "selectedCandidateId", "controlId");
        String type = emptyToNull(firstText(node, "", "targetType", "type"));
        String visibleText = emptyToNull(firstText(node, "", "visibleText", "visibleText"));
        List<String> evidenceUsed = new ArrayList<>();
        JsonNode used = node.path("evidenceUsed");
        if (used.isArray()) {
            for (JsonNode item : used) {
                if (item != null && item.isTextual() && !item.asText("").isBlank()) {
                    evidenceUsed.add(item.asText().trim());
                }
            }
        }
        return new TargetHypothesis(
                firstText(node, "FILTER_TARGET", "classification", "category"),
                emptyToNull(firstText(node, "", "semanticField", "field")),
                emptyToNull(firstText(node, "", "targetValue", "value")),
                emptyToNull(candidateId),
                firstText(node, "resolve_child_of_filter_container", "recommendedStrategy", "strategy"),
                node.path("confidence").asDouble(0.5),
                node.path("visualTargetPresent").asBoolean(false),
                node.path("domResolved").asBoolean(false),
                firstText(node, "", "reason", "explanation"),
                List.copyOf(evidence),
                type,
                visibleText,
                VisualRegion.fromJson(node),
                emptyToNull(firstText(node, "", "decision")),
                emptyToNull(firstText(node, "", "operation")),
                emptyToNull(firstText(node, "", "controlId")),
                emptyToNull(firstText(node, "", "containerId")),
                List.copyOf(evidenceUsed)
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String firstText(JsonNode node, String fallback, String... keys) {
        for (String key : keys) {
            String v = node.path(key).asText("");
            if (!v.isBlank()) {
                return v;
            }
        }
        return fallback;
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
