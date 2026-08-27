package com.smartqa.ai;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

/**
 * Structured telemetry for every multimodal / failure AI call.
 * Never logs secrets, raw DOM dumps, or screenshot bytes.
 */
public final class AiTelemetry {

    private AiTelemetry() {
    }

    public static void callStarted(
            String reason,
            String provider,
            String model,
            int evidenceSize,
            boolean screenshotIncluded,
            boolean domIncluded) {
        callStarted(reason, provider, model, evidenceSize, screenshotIncluded, domIncluded,
                false, false, false, 0, 0);
    }

    public static void callStarted(
            String reason,
            String provider,
            String model,
            int evidenceSize,
            boolean screenshotIncluded,
            boolean domIncluded,
            boolean cdpIncluded,
            boolean axIncluded,
            boolean graphIncluded,
            int keyIndex,
            int keyCount) {
        TraceLogger.info("AI", "AI_CALL_STARTED", "AI team member invoked", TraceMeta.of(
                "reason", nullToEmpty(reason),
                "provider", nullToEmpty(provider),
                "model", nullToEmpty(model),
                "evidenceSize", evidenceSize,
                "screenshotIncluded", screenshotIncluded,
                "domIncluded", domIncluded,
                "cdpIncluded", cdpIncluded,
                "axIncluded", axIncluded,
                "graphIncluded", graphIncluded,
                "keyIndex", keyIndex,
                "keyCount", keyCount
        ));
    }

    public static void callCompleted(
            String reason,
            String provider,
            String model,
            int evidenceSize,
            boolean screenshotIncluded,
            boolean domIncluded,
            long latencyMs,
            String classification,
            double confidence,
            String recommendation,
            Boolean accepted,
            String finalOutcome) {
        TraceLogger.info("AI", "AI_CALL_COMPLETED", "AI team member response recorded", TraceMeta.of(
                "reason", nullToEmpty(reason),
                "provider", nullToEmpty(provider),
                "model", nullToEmpty(model),
                "evidenceSize", evidenceSize,
                "screenshotIncluded", screenshotIncluded,
                "domIncluded", domIncluded,
                "latencyMs", latencyMs,
                "classification", nullToEmpty(classification),
                "confidence", confidence,
                "recommendation", nullToEmpty(recommendation),
                "accepted", accepted == null ? "" : accepted,
                "finalOutcome", nullToEmpty(finalOutcome)
        ));
    }

    public static void consensus(
            boolean agreed,
            boolean reinspect,
            String primaryProvider,
            String secondaryProvider,
            String classification,
            double confidence,
            String reason) {
        TraceLogger.info("AI", "AI_CONSENSUS", "Gemini/Ollama consensus evaluated", TraceMeta.of(
                "agreed", agreed,
                "requiresDeterministicReinspect", reinspect,
                "primaryProvider", nullToEmpty(primaryProvider),
                "secondaryProvider", nullToEmpty(secondaryProvider),
                "classification", nullToEmpty(classification),
                "confidence", confidence,
                "reason", nullToEmpty(reason)
        ));
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
