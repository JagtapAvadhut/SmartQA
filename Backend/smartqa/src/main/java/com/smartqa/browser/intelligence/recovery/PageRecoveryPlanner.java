package com.smartqa.browser.intelligence.recovery;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;

/**
 * Decides whether a missing target is a wrong-page problem. Historical evidence is diagnosis only.
 */
public final class PageRecoveryPlanner {

    private PageRecoveryPlanner() {
    }

    public static RecoveryDecision recommend(
            BrowserStateHistory history,
            String target,
            String currentUrl,
            String currentCompactDom,
            int currentStep) {
        if (history == null || target == null || target.isBlank()) {
            return RecoveryDecision.none("No history or target");
        }
        String needle = target.toLowerCase(Locale.ROOT).trim();
        if (needle.length() < 2) {
            return RecoveryDecision.none("Target too short");
        }
        String currentDom = currentCompactDom == null ? "" : currentCompactDom.toLowerCase(Locale.ROOT);
        if (currentDom.contains(needle)) {
            return RecoveryDecision.none("Current live page already contains the target");
        }
        BrowserStateRecord previous = history.previous();
        if (previous == null) {
            TraceLogger.info("RECOVERY", "RECOVERY_ANALYSIS_STARTED", "No previous state to backtrack to", TraceMeta.of(
                    "target", target,
                    "currentStep", currentStep
            ));
            return RecoveryDecision.none("No previous browser state");
        }
        String previousDom = previous.compactDom() == null ? "" : previous.compactDom().toLowerCase(Locale.ROOT);
        boolean previousHasTarget = previousDom.contains(needle);
        TraceLogger.info("RECOVERY", "RECOVERY_ANALYSIS_STARTED", "Comparing current page with previous state", TraceMeta.of(
                "target", target,
                "currentUrl", currentUrl,
                "previousUrl", previous.url(),
                "previousHasTarget", previousHasTarget,
                "currentHasTarget", false
        ));
        if (!previousHasTarget) {
            return RecoveryDecision.none("Previous state also lacks the target");
        }
        if (1 > history.maxBacktrackSteps()) {
            TraceLogger.warn("RECOVERY", "RECOVERY_EXHAUSTED", "Backtrack depth exceeded", TraceMeta.of(
                    "max", history.maxBacktrackSteps()
            ));
            return RecoveryDecision.none("Backtrack limit reached");
        }
        TraceLogger.info("RECOVERY", "PREVIOUS_STATE_FOUND", "Previous state appears to contain the target", TraceMeta.of(
                "targetStateStep", previous.stepNumber(),
                "currentStep", currentStep,
                "screenshotRef", previous.screenshotRef() == null ? "" : previous.screenshotRef()
        ));
        TraceLogger.info("RECOVERY", "PREVIOUS_STATE_SCREENSHOT_ANALYZED",
                "Historical screenshot is diagnostic only; live DOM after backtrack must win", TraceMeta.of(
                        "screenshotRef", previous.screenshotRef() == null ? "" : previous.screenshotRef(),
                        "executionAuthorized", false
                ));
        TraceLogger.info("RECOVERY", "BACKTRACK_APPROVED", "Playwright goBack is justified", TraceMeta.of(
                "confidence", 0.86,
                "recoveryAction", RecoveryDecision.BACK
        ));
        return new RecoveryDecision(
                true,
                "Expected target existed on the immediately previous state, not the current page",
                previous.stepNumber(),
                currentStep,
                RecoveryDecision.BACK,
                0.86
        );
    }
}
