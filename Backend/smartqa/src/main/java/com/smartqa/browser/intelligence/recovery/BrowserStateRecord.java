package com.smartqa.browser.intelligence.recovery;

import java.time.Instant;

/**
 * Sanitized historical browser state. Diagnosis only — never execution proof.
 */
public record BrowserStateRecord(
        int stepNumber,
        String url,
        String title,
        Instant timestamp,
        String screenshotRef,
        String compactDom,
        String action,
        String target,
        boolean actionSucceeded,
        String assertionState
) {
}
