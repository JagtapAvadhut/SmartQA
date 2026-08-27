package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.RelevantDomExtractor;

import java.time.Instant;
import java.util.List;

/**
 * Correlated screenshot + compact DOM + a11y + candidates from the same browser moment.
 */
public record BrowserEvidenceBundle(
        String instruction,
        String normalizedIntent,
        String action,
        String semanticField,
        String targetValue,
        String url,
        String pageTitle,
        int viewportWidth,
        int viewportHeight,
        byte[] screenshotPng,
        String relevantDom,
        String relationshipGraph,
        String layoutRegions,
        String frameContext,
        String shadowContext,
        int candidateCount,
        Instant capturedAt,
        String momentId,
        String cdpStatus,
        List<String> previousAttempts
) {
    public BrowserEvidenceBundle {
        previousAttempts = previousAttempts == null ? List.of() : List.copyOf(previousAttempts);
        screenshotPng = screenshotPng == null ? new byte[0] : screenshotPng;
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        momentId = momentId == null || momentId.isBlank() ? java.util.UUID.randomUUID().toString() : momentId;
        cdpStatus = cdpStatus == null || cdpStatus.isBlank() ? "unavailable" : cdpStatus;
    }

    public boolean screenshotIncluded() {
        return screenshotPng.length > 0;
    }

    public int evidenceSize() {
        return (relevantDom == null ? 0 : relevantDom.length())
                + (relationshipGraph == null ? 0 : relationshipGraph.length())
                + screenshotPng.length;
    }

    public String toPromptText() {
        return """
                Instruction: %s
                Normalized intent: %s
                Action: %s
                Semantic field: %s
                Target value: %s
                URL: %s
                Title: %s
                Viewport: %dx%d
                Frame: %s
                Shadow: %s
                Candidate count: %d
                Previous attempts: %s
                Layout regions:
                %s
                Parent/child graph:
                %s
                Compact relevant DOM:
                %s
                Rules: Return JSON only. Prefer candidateId from the DOM list. Never invent CSS/XPath. Never return Playwright. Never use coordinates as execution truth. Live DOM must still be verified.
                """.formatted(
                nullToEmpty(instruction),
                nullToEmpty(normalizedIntent),
                nullToEmpty(action),
                nullToEmpty(semanticField),
                nullToEmpty(targetValue),
                nullToEmpty(url),
                nullToEmpty(pageTitle),
                viewportWidth,
                viewportHeight,
                nullToEmpty(frameContext),
                nullToEmpty(shadowContext),
                candidateCount,
                previousAttempts.isEmpty() ? "none" : String.join("; ", previousAttempts),
                nullToEmpty(layoutRegions),
                nullToEmpty(relationshipGraph),
                nullToEmpty(relevantDom)
        );
    }

    public static String compactDom(List<ElementCandidate> elements, String target, String owner) {
        return RelevantDomExtractor.compact(elements, target, owner, 40);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
