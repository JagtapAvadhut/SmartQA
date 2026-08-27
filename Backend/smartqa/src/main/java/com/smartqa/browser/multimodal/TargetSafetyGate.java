package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ActionCompatibility;
import com.smartqa.browser.intelligence.ControlClassifier;
import com.smartqa.browser.intelligence.ControlType;
import com.smartqa.browser.intelligence.DomEvidence;
import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.LocatorRanker;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.List;
import java.util.Locale;

/**
 * AI proposes. This gate verifies against the live candidate list before Playwright runs.
 */
public final class TargetSafetyGate {

    public record GateResult(
            boolean accepted,
            LocatorRanker.RankedElement ranked,
            String reason,
            String outcome
    ) {
        public static GateResult reject(String reason) {
            return new GateResult(false, null, reason, "AI_IDENTIFIED_LIVE_VERIFICATION_FAILED");
        }
    }

    private TargetSafetyGate() {
    }

    public static GateResult verify(
            TargetHypothesis hypothesis,
            List<LocatorRanker.RankedElement> ranked,
            SemanticTargetNormalizer.NormalizedTarget intent) {
        if (hypothesis == null) {
            TraceLogger.warn("AI", "SAFETY_GATE_REJECTED", "Safety gate rejected empty hypothesis", TraceMeta.of(
                    "reason", "No hypothesis"
            ));
            return GateResult.reject("No hypothesis");
        }
        if (TargetHypothesis.forbiddenStrategy(hypothesis.recommendedStrategy())) {
            TraceLogger.warn("AI", "AI_STRATEGY_REJECTED", "Unsafe AI strategy rejected", TraceMeta.of(
                    "strategy", hypothesis.recommendedStrategy()
            ));
            return GateResult.reject("Unsafe strategy");
        }
        if (!hypothesis.isSafeToAttempt()) {
            if ("VISUAL_TARGET_PRESENT_DOM_UNRESOLVED".equalsIgnoreCase(hypothesis.classification())) {
                return new GateResult(false, null, hypothesis.reason(), hypothesis.classification());
            }
            if ("TARGET_NOT_PRESENT".equalsIgnoreCase(hypothesis.classification())) {
                return new GateResult(false, null, hypothesis.reason(), "TARGET_NOT_PRESENT");
            }
            return GateResult.reject("Hypothesis not actionable");
        }
        LocatorRanker.RankedElement match = findById(ranked, hypothesis.liveCandidateId());
        if (match == null) {
            TraceLogger.warn("AI", "SAFETY_GATE_REJECTED", "Recommended candidate is not in the live set", TraceMeta.of(
                    "recommendedCandidateId", hypothesis.recommendedCandidateId(),
                    "controlId", hypothesis.controlId(),
                    "liveCandidateCount", ranked == null ? 0 : ranked.size()
            ));
            return GateResult.reject("recommendedCandidateId not in live candidate set");
        }
        ElementCandidate el = match.element();
        if (!el.visible()) {
            return GateResult.reject("Candidate not visible");
        }
        if (el.structureOrEmpty().covered()) {
            return GateResult.reject("Candidate is covered");
        }
        if (!el.structureOrEmpty().reconciled()) {
            return GateResult.reject("Tree/graph evidence is inconsistent");
        }
        if (!el.enabled() || el.disabled()) {
            return GateResult.reject("Candidate not enabled");
        }
        if (hypothesis.containerId() != null && !hypothesis.containerId().isBlank()) {
            String wanted = hypothesis.containerId().toLowerCase(Locale.ROOT);
            boolean owned = wanted.equalsIgnoreCase(el.containerId())
                    || (el.ancestorIds() != null && el.ancestorIds().toLowerCase(Locale.ROOT).contains(wanted))
                    || (el.parentId() != null && el.parentId().equalsIgnoreCase(hypothesis.containerId()))
                    || DomEvidence.ownsContext(el, hypothesis.containerId());
            if (!owned) {
                return GateResult.reject("Candidate is not owned by requested container");
            }
        }
        if (hypothesis.operation() != null && !hypothesis.operation().isBlank()) {
            ControlType liveType = ControlClassifier.classifyFromCandidate(el);
            boolean strictWidget = isStrictWidget(hypothesis.operation());
            if (!ActionCompatibility.isCompatible(hypothesis.operation(), liveType)
                    && liveType != ControlType.LABEL
                    && (strictWidget
                    || (liveType != ControlType.HEADING
                    && liveType != ControlType.TEXT
                    && liveType != ControlType.OTHER))) {
                return GateResult.reject("CAPABILITY_MISMATCH");
            }
        }
        if (hypothesis.visualRegion() != null) {
            boolean overlaps = hypothesis.visualRegion().overlaps(el.boundingBox())
                    || hypothesis.visualRegion().containsCenterOf(el.boundingBox());
            if (!overlaps) {
                TraceLogger.warn("AI", "VISUAL_REGION_REJECTED", "Visual region does not map to the live candidate", TraceMeta.of(
                        "candidateId", el.candidateId()
                ));
                return GateResult.reject("Visual region does not overlap a live candidate");
            }
            if (!el.clickable()) {
                return new GateResult(false, null, "Visual target has no actionable live candidate", "VISUAL_TARGET_PRESENT_DOM_UNRESOLVED");
            }
            TraceLogger.info("AI", "VISUAL_DOM_MAPPING_FOUND", "Visual region mapped to live candidate", TraceMeta.of(
                    "candidateId", el.candidateId()
            ));
        }
        if (intent != null && intent.isFilterOption()) {
            if (notBlank(intent.semanticField()) && !DomEvidence.ownsContext(el, intent.semanticField())) {
                boolean headingOwns = (el.headingContext() + " " + el.ancestorContext())
                        .toLowerCase(Locale.ROOT)
                        .contains(intent.semanticField().toLowerCase(Locale.ROOT));
                if (!headingOwns) {
                    return GateResult.reject("Candidate is not owned by requested filter field");
                }
            }
            if (notBlank(intent.value()) && !valueMatches(el, intent.value())) {
                return GateResult.reject("Candidate value does not match requested option");
            }
        }
        TraceLogger.info("AI", "SAFETY_GATE_ACCEPTED", "AI hypothesis verified against live DOM", TraceMeta.of(
                "candidateId", el.candidateId(),
                "confidence", hypothesis.confidence(),
                "strategy", hypothesis.recommendedStrategy()
        ));
        return new GateResult(true, match, "verified", "ACCEPTED");
    }

    private static LocatorRanker.RankedElement findById(List<LocatorRanker.RankedElement> ranked, String candidateId) {
        if (ranked == null || candidateId == null || candidateId.isBlank()) {
            return null;
        }
        String wanted = candidateId.trim();
        for (LocatorRanker.RankedElement rankedElement : ranked) {
            if (wanted.equalsIgnoreCase(rankedElement.element().candidateId())) {
                return rankedElement;
            }
        }
        // Allow candidate-A style aliases mapped by rank order.
        if (wanted.toUpperCase(Locale.ROOT).startsWith("CANDIDATE-") && wanted.length() >= 11) {
            int index = wanted.charAt(wanted.length() - 1) - 'A';
            if (index >= 0 && index < ranked.size()) {
                return ranked.get(index);
            }
        }
        return null;
    }

    private static boolean valueMatches(ElementCandidate el, String value) {
        String blob = (el.accessibleName() + " " + el.text() + " " + el.label() + " " + el.value())
                .toLowerCase(Locale.ROOT);
        return blob.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static boolean isStrictWidget(String operation) {
        if (operation == null || operation.isBlank()) {
            return false;
        }
        String lower = operation.toLowerCase(Locale.ROOT);
        return "checkbox".equals(lower) || "radio".equals(lower)
                || "select".equals(lower) || "input".equals(lower);
    }
}
