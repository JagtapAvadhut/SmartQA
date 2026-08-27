package com.smartqa.pipeline;

import com.smartqa.ai.AiMediaPart;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fresh multimodal evidence package for Gemini / Ollama diagnosis.
 * Built immediately before each AI call — never reuse stale DOM or screenshots.
 */
public record AiEvidenceBundle(
        String traceId,
        String runId,
        Integer stepNumber,
        String instruction,
        String action,
        String target,
        String location,
        String currentUrl,
        String pageTitle,
        String screenshotPath,
        byte[] screenshotBytes,
        String screenshotMimeType,
        String domExcerpt,
        String accessibilityExcerpt,
        String visibleTextExcerpt,
        String frameContext,
        String shadowContext,
        List<String> candidateLocators,
        List<Double> candidateScores,
        String selectedLocator,
        String actionability,
        String beforeState,
        String afterState,
        List<String> previousAttempts,
        Instant capturedAt
) {
    private static final int MAX_SCREENSHOT_BYTES = 1_500_000;
    private static final int DOM_LIMIT = 1800;
    private static final int A11Y_LIMIT = 900;
    private static final int TEXT_LIMIT = 900;

    public AiEvidenceBundle {
        candidateLocators = candidateLocators == null ? List.of() : List.copyOf(candidateLocators);
        candidateScores = candidateScores == null ? List.of() : List.copyOf(candidateScores);
        previousAttempts = previousAttempts == null ? List.of() : List.copyOf(previousAttempts);
        screenshotBytes = screenshotBytes == null ? new byte[0] : screenshotBytes;
        screenshotMimeType = screenshotMimeType == null || screenshotMimeType.isBlank()
                ? "image/png" : screenshotMimeType;
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }

    /**
     * Creates a fresh bundle from pipeline evidence, loading screenshot bytes when present on disk.
     */
    public static AiEvidenceBundle from(FailureEvidence evidence) {
        if (evidence == null) {
            return empty();
        }
        byte[] bytes = loadScreenshot(evidence.screenshotPath());
        String mime = guessMime(evidence.screenshotPath());
        String actionability = extrasString(evidence.extras(), "actionability");
        return new AiEvidenceBundle(
                evidence.traceId(),
                evidence.runId(),
                evidence.stepNumber(),
                maskSecrets(evidence.instruction()),
                maskSecrets(evidence.action()),
                maskSecrets(evidence.target()),
                maskSecrets(evidence.location()),
                evidence.url(),
                evidence.pageTitle(),
                evidence.screenshotPath(),
                bytes,
                mime,
                truncate(maskSecrets(evidence.domExcerpt()), DOM_LIMIT),
                truncate(maskSecrets(evidence.accessibilityExcerpt()), A11Y_LIMIT),
                truncate(maskSecrets(evidence.visibleTextExcerpt()), TEXT_LIMIT),
                evidence.frameContext(),
                evidence.shadowContext(),
                evidence.candidateLocators(),
                evidence.candidateScores(),
                selectedLocatorOf(evidence),
                actionability,
                truncate(maskSecrets(evidence.beforeState()), 400),
                truncate(maskSecrets(evidence.afterState()), 400),
                evidence.previousAttempts(),
                Instant.now()
        );
    }

    private static String selectedLocatorOf(FailureEvidence evidence) {
        String type = evidence.locatorType();
        String value = evidence.locator();
        if ((type == null || type.isBlank()) && (value == null || value.isBlank())) {
            return "";
        }
        if (type == null || type.isBlank()) {
            return nullToEmpty(value);
        }
        return nullToEmpty(type) + "=" + nullToEmpty(value);
    }

    /**
     * Before-action assist bundle from live page capture + ranked candidates.
     */
    public static AiEvidenceBundle forBeforeAction(
            String url,
            String title,
            String action,
            String target,
            String instruction,
            List<String> candidateLocators,
            List<Double> candidateScores,
            byte[] screenshotPng,
            String domExcerpt,
            String accessibilityExcerpt) {
        return new AiEvidenceBundle(
                null,
                null,
                null,
                maskSecrets(instruction),
                maskSecrets(action),
                maskSecrets(target),
                null,
                url,
                title,
                null,
                limitScreenshot(screenshotPng),
                "image/png",
                truncate(maskSecrets(domExcerpt), DOM_LIMIT),
                truncate(maskSecrets(accessibilityExcerpt), A11Y_LIMIT),
                "",
                null,
                null,
                candidateLocators,
                candidateScores,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.now()
        );
    }

    public static AiEvidenceBundle empty() {
        return new AiEvidenceBundle(
                null, null, null, null, null, null, null, null, null, null,
                new byte[0], "image/png", null, null, null, null, null,
                List.of(), List.of(), null, null, null, null, List.of(), Instant.now());
    }

    public boolean screenshotIncluded() {
        return screenshotBytes.length > 0;
    }

    public boolean domIncluded() {
        return domExcerpt != null && !domExcerpt.isBlank();
    }

    public boolean accessibilityIncluded() {
        return accessibilityExcerpt != null && !accessibilityExcerpt.isBlank();
    }

    /** Truthful: browser URL was captured. Never invent a live session. */
    public boolean browserEvidencePresent() {
        return currentUrl != null && !currentUrl.isBlank();
    }

    /** Truthful: an assertion before/after snapshot was attached. */
    public boolean assertionEvidencePresent() {
        return (beforeState != null && !beforeState.isBlank())
                || (afterState != null && !afterState.isBlank());
    }

    public boolean candidatesPresent() {
        return candidateLocators != null && !candidateLocators.isEmpty();
    }

    public int evidenceSize() {
        return toCompactText().length() + screenshotBytes.length;
    }

    public List<AiMediaPart> mediaParts() {
        if (!screenshotIncluded()) {
            return List.of();
        }
        return List.of(AiMediaPart.image(screenshotBytes, screenshotMimeType));
    }

    public String toCompactText() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("URL: ").append(nullToEmpty(currentUrl)).append('\n');
        sb.append("Title: ").append(nullToEmpty(pageTitle)).append('\n');
        sb.append("Step: ").append(stepNumber == null ? "?" : stepNumber).append('\n');
        sb.append("Instruction: ").append(nullToEmpty(instruction)).append('\n');
        sb.append("Action: ").append(nullToEmpty(action)).append('\n');
        sb.append("Target: ").append(nullToEmpty(target)).append('\n');
        sb.append("Location: ").append(nullToEmpty(location)).append('\n');
        sb.append("Selected locator: ").append(nullToEmpty(selectedLocator)).append('\n');
        sb.append("Actionability: ").append(nullToEmpty(actionability)).append('\n');
        if (candidateLocators != null && !candidateLocators.isEmpty()) {
            sb.append("Candidates:\n");
            int n = Math.min(8, candidateLocators.size());
            for (int i = 0; i < n; i++) {
                String id = "candidate-" + (char) ('A' + i);
                Double score = candidateScores != null && i < candidateScores.size()
                        ? candidateScores.get(i) : null;
                sb.append("  ").append(id).append(": ").append(candidateLocators.get(i));
                if (score != null) {
                    sb.append(" (score=").append(score).append(')');
                }
                sb.append('\n');
            }
        }
        if (previousAttempts != null && !previousAttempts.isEmpty()) {
            sb.append("Previous recovery attempts: ")
                    .append(String.join("; ", previousAttempts.stream().limit(6).toList()))
                    .append('\n');
        }
        sb.append("Before-state: ").append(nullToEmpty(beforeState)).append('\n');
        sb.append("After-state: ").append(nullToEmpty(afterState)).append('\n');
        sb.append("Frame: ").append(nullToEmpty(frameContext)).append('\n');
        sb.append("Shadow: ").append(nullToEmpty(shadowContext)).append('\n');
        sb.append("Screenshot attached: ").append(screenshotIncluded()).append('\n');
        sb.append("Evidence flags: screenshot=").append(screenshotIncluded())
                .append(" dom=").append(domIncluded())
                .append(" a11y=").append(accessibilityIncluded())
                .append(" browser=").append(browserEvidencePresent())
                .append(" assertion=").append(assertionEvidencePresent())
                .append(" candidates=").append(candidatesPresent())
                .append('\n');
        sb.append("Visible text excerpt:\n").append(nullToEmpty(visibleTextExcerpt)).append('\n');
        sb.append("Accessibility excerpt:\n").append(nullToEmpty(accessibilityExcerpt)).append('\n');
        sb.append("DOM excerpt:\n").append(nullToEmpty(domExcerpt)).append('\n');
        sb.append("Rules: Prefer candidateId (candidate-A..) or semantic strategy. ")
                .append("Never invent authoritative CSS/XPath. Never weaken assertions. Never return credentials.\n");
        return sb.toString();
    }

    static String maskSecrets(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String masked = value;
        masked = masked.replaceAll("(?i)(api[_-]?key|password|passwd|secret|token|bearer|authorization)\\s*[=:]\\s*[^\\s,;]+",
                "$1=***");
        masked = masked.replaceAll("(?i)\\b(sk-[A-Za-z0-9]{10,})\\b", "***");
        return masked;
    }

    private static byte[] loadScreenshot(String path) {
        if (path == null || path.isBlank()) {
            return new byte[0];
        }
        try {
            Path p = Path.of(path);
            if (!Files.isRegularFile(p)) {
                return new byte[0];
            }
            return limitScreenshot(Files.readAllBytes(p));
        } catch (Exception ex) {
            return new byte[0];
        }
    }

    private static byte[] limitScreenshot(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new byte[0];
        }
        if (bytes.length <= MAX_SCREENSHOT_BYTES) {
            return bytes;
        }
        // Prefer sending nothing oversized rather than truncating binary PNG.
        return new byte[0];
    }

    private static String guessMime(String path) {
        if (path == null) {
            return "image/png";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private static String extrasString(Map<String, Object> extras, String key) {
        if (extras == null || key == null) {
            return "";
        }
        Object v = extras.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    public List<String> candidateIds() {
        List<String> ids = new ArrayList<>();
        int n = Math.min(8, candidateLocators.size());
        for (int i = 0; i < n; i++) {
            ids.add("candidate-" + (char) ('A' + i));
        }
        return ids;
    }
}
