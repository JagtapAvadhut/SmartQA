package com.smartqa.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Compact structured evidence for a single pipeline/browser failure.
 * Never dumps the full DOM — only relevant excerpts for diagnosis.
 */
public record FailureEvidence(
        String traceId,
        String pipelineId,
        String runId,
        String testCaseId,
        String website,
        String url,
        String pageTitle,
        Integer stepNumber,
        String instruction,
        String action,
        String target,
        String location,
        String expected,
        String actual,
        String failureCategory,
        String exception,
        String locator,
        String locatorType,
        Double confidence,
        List<String> candidateLocators,
        List<Double> candidateScores,
        String domExcerpt,
        String accessibilityExcerpt,
        String visibleTextExcerpt,
        String frameContext,
        String shadowContext,
        String overlayEvidence,
        String beforeState,
        String afterState,
        String previousStep,
        String nextStep,
        String screenshotPath,
        List<String> previousAttempts,
        Instant timestamp,
        Long durationMs,
        Map<String, Object> extras
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toCompactMap() {
        return Map.ofEntries(
                Map.entry("traceId", nullToEmpty(traceId)),
                Map.entry("pipelineId", nullToEmpty(pipelineId)),
                Map.entry("runId", nullToEmpty(runId)),
                Map.entry("testCaseId", nullToEmpty(testCaseId)),
                Map.entry("website", nullToEmpty(website)),
                Map.entry("url", nullToEmpty(url)),
                Map.entry("pageTitle", nullToEmpty(pageTitle)),
                Map.entry("stepNumber", stepNumber == null ? 0 : stepNumber),
                Map.entry("instruction", nullToEmpty(instruction)),
                Map.entry("action", nullToEmpty(action)),
                Map.entry("target", nullToEmpty(target)),
                Map.entry("location", nullToEmpty(location)),
                Map.entry("expected", nullToEmpty(expected)),
                Map.entry("actual", nullToEmpty(actual)),
                Map.entry("failureCategory", nullToEmpty(failureCategory)),
                Map.entry("exception", truncate(exception, 500)),
                Map.entry("locator", nullToEmpty(locator)),
                Map.entry("locatorType", nullToEmpty(locatorType)),
                Map.entry("confidence", confidence == null ? 0.0 : confidence),
                Map.entry("candidateLocators", candidateLocators == null ? List.of() : candidateLocators),
                Map.entry("candidateScores", candidateScores == null ? List.of() : candidateScores),
                Map.entry("domExcerpt", truncate(domExcerpt, 2500)),
                Map.entry("accessibilityExcerpt", truncate(accessibilityExcerpt, 1200)),
                Map.entry("visibleTextExcerpt", truncate(visibleTextExcerpt, 1500)),
                Map.entry("frameContext", nullToEmpty(frameContext)),
                Map.entry("shadowContext", nullToEmpty(shadowContext)),
                Map.entry("overlayEvidence", truncate(overlayEvidence, 400)),
                Map.entry("beforeState", truncate(beforeState, 400)),
                Map.entry("afterState", truncate(afterState, 400)),
                Map.entry("previousStep", nullToEmpty(previousStep)),
                Map.entry("nextStep", nullToEmpty(nextStep)),
                Map.entry("screenshotPath", nullToEmpty(screenshotPath)),
                Map.entry("previousAttempts", previousAttempts == null ? List.of() : previousAttempts),
                Map.entry("timestamp", timestamp == null ? Instant.EPOCH.toString() : timestamp.toString()),
                Map.entry("durationMs", durationMs == null ? 0L : durationMs)
        );
    }

    public String toAiContext() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("URL: ").append(nullToEmpty(url)).append('\n');
        sb.append("Title: ").append(nullToEmpty(pageTitle)).append('\n');
        sb.append("Website: ").append(nullToEmpty(website)).append('\n');
        sb.append("Step: ").append(stepNumber == null ? "?" : stepNumber).append('\n');
        sb.append("Action: ").append(nullToEmpty(action)).append('\n');
        sb.append("Target: ").append(nullToEmpty(target)).append('\n');
        sb.append("Instruction: ").append(nullToEmpty(instruction)).append('\n');
        sb.append("Expected: ").append(nullToEmpty(expected)).append('\n');
        sb.append("Actual: ").append(nullToEmpty(actual)).append('\n');
        sb.append("Category hint: ").append(nullToEmpty(failureCategory)).append('\n');
        sb.append("Exception: ").append(truncate(exception, 400)).append('\n');
        sb.append("Locator: ");
        if ((locatorType == null || locatorType.isBlank()) && (locator == null || locator.isBlank())) {
            sb.append("(missing — no locator captured)").append('\n');
        } else {
            sb.append(nullToEmpty(locatorType)).append('=').append(nullToEmpty(locator)).append('\n');
        }
        if (candidateLocators != null && !candidateLocators.isEmpty()) {
            sb.append("Candidates: ").append(String.join(" | ", candidateLocators.stream().limit(8).toList())).append('\n');
        }
        if (candidateScores != null && !candidateScores.isEmpty()) {
            sb.append("Candidate scores: ").append(candidateScores.stream().limit(8).map(String::valueOf).toList()).append('\n');
        }
        if (previousAttempts != null && !previousAttempts.isEmpty()) {
            sb.append("Previous attempts: ").append(String.join("; ", previousAttempts.stream().limit(5).toList())).append('\n');
        }
        sb.append("Before-state: ").append(truncate(beforeState, 300)).append('\n');
        sb.append("After-state: ").append(truncate(afterState, 300)).append('\n');
        sb.append("Screenshot path: ").append(nullToEmpty(screenshotPath)).append('\n');
        sb.append("Accessibility excerpt:\n").append(truncate(accessibilityExcerpt, 600)).append('\n');
        sb.append("Visible text excerpt:\n").append(truncate(visibleTextExcerpt, 800)).append('\n');
        sb.append("DOM excerpt:\n").append(truncate(domExcerpt, 1200)).append('\n');
        if (overlayEvidence != null && !overlayEvidence.isBlank()) {
            sb.append("Overlay: ").append(truncate(overlayEvidence, 200)).append('\n');
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    public static final class Builder {
        private String traceId;
        private String pipelineId;
        private String runId;
        private String testCaseId;
        private String website;
        private String url;
        private String pageTitle;
        private Integer stepNumber;
        private String instruction;
        private String action;
        private String target;
        private String location;
        private String expected;
        private String actual;
        private String failureCategory;
        private String exception;
        private String locator;
        private String locatorType;
        private Double confidence;
        private List<String> candidateLocators = List.of();
        private List<Double> candidateScores = List.of();
        private String domExcerpt;
        private String accessibilityExcerpt;
        private String visibleTextExcerpt;
        private String frameContext;
        private String shadowContext;
        private String overlayEvidence;
        private String beforeState;
        private String afterState;
        private String previousStep;
        private String nextStep;
        private String screenshotPath;
        private List<String> previousAttempts = List.of();
        private Instant timestamp = Instant.now();
        private Long durationMs;
        private Map<String, Object> extras = Map.of();

        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder pipelineId(String v) { this.pipelineId = v; return this; }
        public Builder runId(String v) { this.runId = v; return this; }
        public Builder testCaseId(String v) { this.testCaseId = v; return this; }
        public Builder website(String v) { this.website = v; return this; }
        public Builder url(String v) { this.url = v; return this; }
        public Builder pageTitle(String v) { this.pageTitle = v; return this; }
        public Builder stepNumber(Integer v) { this.stepNumber = v; return this; }
        public Builder instruction(String v) { this.instruction = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder target(String v) { this.target = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder expected(String v) { this.expected = v; return this; }
        public Builder actual(String v) { this.actual = v; return this; }
        public Builder failureCategory(String v) { this.failureCategory = v; return this; }
        public Builder exception(String v) { this.exception = v; return this; }
        public Builder locator(String v) { this.locator = v; return this; }
        public Builder locatorType(String v) { this.locatorType = v; return this; }
        public Builder confidence(Double v) { this.confidence = v; return this; }
        public Builder candidateLocators(List<String> v) { this.candidateLocators = v == null ? List.of() : List.copyOf(v); return this; }
        public Builder candidateScores(List<Double> v) { this.candidateScores = v == null ? List.of() : List.copyOf(v); return this; }
        public Builder domExcerpt(String v) { this.domExcerpt = v; return this; }
        public Builder accessibilityExcerpt(String v) { this.accessibilityExcerpt = v; return this; }
        public Builder visibleTextExcerpt(String v) { this.visibleTextExcerpt = v; return this; }
        public Builder frameContext(String v) { this.frameContext = v; return this; }
        public Builder shadowContext(String v) { this.shadowContext = v; return this; }
        public Builder overlayEvidence(String v) { this.overlayEvidence = v; return this; }
        public Builder beforeState(String v) { this.beforeState = v; return this; }
        public Builder afterState(String v) { this.afterState = v; return this; }
        public Builder previousStep(String v) { this.previousStep = v; return this; }
        public Builder nextStep(String v) { this.nextStep = v; return this; }
        public Builder screenshotPath(String v) { this.screenshotPath = v; return this; }
        public Builder previousAttempts(List<String> v) { this.previousAttempts = v == null ? List.of() : List.copyOf(v); return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }
        public Builder durationMs(Long v) { this.durationMs = v; return this; }
        public Builder extras(Map<String, Object> v) { this.extras = v == null ? Map.of() : Map.copyOf(v); return this; }

        public FailureEvidence build() {
            return new FailureEvidence(
                    traceId, pipelineId, runId, testCaseId, website, url, pageTitle, stepNumber,
                    instruction, action, target, location, expected, actual, failureCategory, exception,
                    locator, locatorType, confidence, candidateLocators, candidateScores,
                    domExcerpt, accessibilityExcerpt, visibleTextExcerpt, frameContext, shadowContext,
                    overlayEvidence, beforeState, afterState, previousStep, nextStep, screenshotPath,
                    previousAttempts, timestamp, durationMs, extras);
        }
    }
}
