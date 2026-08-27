package com.smartqa.browser;

import java.time.Instant;

/**
 * Canonical search semantic state. Click/fill success is not verification.
 */
public record SearchState(
        String requestedValue,
        String selectedValue,
        String selectedSource,
        Phase inputState,
        Phase suggestionState,
        Phase resultState,
        String currentUrl,
        String currentHost,
        String resultContext,
        double confidence,
        VerificationStatus verificationStatus,
        Instant timestamp
) {
    public enum Phase {
        NOT_STARTED,
        INPUT_FOUND,
        INPUT_FILLED,
        SUGGESTIONS_VISIBLE,
        SUGGESTION_SELECTED,
        SEARCH_SUBMITTED,
        RESULTS_READY,
        VERIFIED,
        MISMATCH,
        FAILED
    }

    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        MISMATCH,
        FAILED
    }

    public boolean blockingLaterSteps() {
        return verificationStatus == VerificationStatus.MISMATCH
                || verificationStatus == VerificationStatus.FAILED
                || resultState == Phase.MISMATCH
                || resultState == Phase.FAILED;
    }

    public SearchState withVerification(VerificationStatus status, Phase result, double confidence) {
        return new SearchState(
                requestedValue,
                selectedValue,
                selectedSource,
                inputState,
                suggestionState,
                result,
                currentUrl,
                currentHost,
                resultContext,
                confidence,
                status,
                Instant.now()
        );
    }
}
