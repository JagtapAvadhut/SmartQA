package com.smartqa.browser;

import java.time.Instant;

/**
 * Location requested / selected / verified are separate fields.
 * Mumbai must never be silently treated as Nagpur.
 */
public record LocationState(
        String requested,
        String selected,
        String verified,
        SearchState.VerificationStatus verificationStatus,
        Instant timestamp
) {
    public static LocationState pending(String requested, String selected) {
        return new LocationState(
                requested == null ? "" : requested,
                selected == null ? "" : selected,
                "",
                SearchState.VerificationStatus.PENDING,
                Instant.now()
        );
    }

    public boolean blockingLaterSteps() {
        return verificationStatus == SearchState.VerificationStatus.MISMATCH
                || verificationStatus == SearchState.VerificationStatus.FAILED;
    }

    public LocationState verified(String verifiedValue) {
        return new LocationState(
                requested,
                selected,
                verifiedValue == null ? "" : verifiedValue,
                SearchState.VerificationStatus.VERIFIED,
                Instant.now()
        );
    }

    public LocationState mismatch(String actual) {
        return new LocationState(
                requested,
                selected,
                actual == null ? "" : actual,
                SearchState.VerificationStatus.MISMATCH,
                Instant.now()
        );
    }
}
