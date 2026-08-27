package com.smartqa.ai;

import java.util.List;

/**
 * Sanitized Gemini pool view for health and tests. Never contains API key material.
 */
public record GeminiPoolSnapshot(
        int configuredKeys,
        int healthyKeys,
        int cooldownKeys,
        int availableKeys,
        String status,
        List<KeyView> keys
) {
    public GeminiPoolSnapshot {
        keys = keys == null ? List.of() : List.copyOf(keys);
        status = status == null || status.isBlank() ? "UNAVAILABLE" : status;
    }

    public record KeyView(
            int keyIndex,
            String state,
            String failureType,
            int failureCount,
            Long lastFailureAt,
            Long cooldownUntil
    ) {
    }
}
