package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;

import java.time.Duration;

/**
 * Single cooldown / rotation policy for every Gemini capability.
 * Durations live here — not in providers.
 */
public final class GeminiRotationPolicy {

    private final boolean enabled;
    private final Duration rateLimitCooldown;
    private final Duration transientCooldown;
    private final Duration invalidKeyCooldown;
    private final String maxKeyAttempts;
    private final int retryPerKey;

    public GeminiRotationPolicy(
            boolean enabled,
            Duration rateLimitCooldown,
            Duration transientCooldown,
            Duration invalidKeyCooldown,
            String maxKeyAttempts,
            int retryPerKey) {
        this.enabled = enabled;
        this.rateLimitCooldown = rateLimitCooldown == null ? Duration.ofSeconds(60) : rateLimitCooldown;
        this.transientCooldown = transientCooldown == null ? Duration.ofSeconds(15) : transientCooldown;
        this.invalidKeyCooldown = invalidKeyCooldown == null ? Duration.ofSeconds(300) : invalidKeyCooldown;
        this.maxKeyAttempts = maxKeyAttempts == null || maxKeyAttempts.isBlank() ? "all" : maxKeyAttempts.trim();
        this.retryPerKey = Math.max(1, retryPerKey);
    }

    public static GeminiRotationPolicy defaults() {
        return new GeminiRotationPolicy(
                true,
                Duration.ofSeconds(60),
                Duration.ofSeconds(15),
                Duration.ofSeconds(300),
                "all",
                1);
    }

    public static GeminiRotationPolicy from(SmartQaProperties properties) {
        if (properties == null || properties.getAi() == null || properties.getAi().getGemini() == null) {
            return defaults();
        }
        return from(properties.getAi().getGemini());
    }

    public static GeminiRotationPolicy from(SmartQaProperties.Gemini gemini) {
        if (gemini == null || gemini.getRotation() == null) {
            return defaults();
        }
        SmartQaProperties.Gemini.Rotation rotation = gemini.getRotation();
        return new GeminiRotationPolicy(
                rotation.isEnabled(),
                Duration.ofSeconds(Math.max(1, rotation.getCooldownSeconds())),
                Duration.ofSeconds(Math.max(1, rotation.getTransientCooldownSeconds())),
                Duration.ofSeconds(Math.max(1, rotation.getInvalidKeyCooldownSeconds())),
                rotation.getMaxKeyAttempts(),
                rotation.getRetryPerKey());
    }

    public boolean enabled() {
        return enabled;
    }

    public Duration cooldownFor(GeminiFailureKind kind) {
        if (kind == null) {
            return transientCooldown;
        }
        return switch (kind) {
            case RATE_LIMITED -> rateLimitCooldown;
            case AUTH -> invalidKeyCooldown;
            case SCHEMA -> Duration.ZERO;
            case TIMEOUT, SERVER_ERROR, UNKNOWN -> transientCooldown;
        };
    }

    public int maxAttempts(int keyCount) {
        if (!enabled) {
            return 1;
        }
        if (keyCount <= 0) {
            return 0;
        }
        if ("all".equalsIgnoreCase(maxKeyAttempts)) {
            return keyCount;
        }
        try {
            return Math.max(1, Math.min(keyCount, Integer.parseInt(maxKeyAttempts)));
        } catch (NumberFormatException ex) {
            return keyCount;
        }
    }

    public int retryPerKey() {
        return retryPerKey;
    }

    public Duration rateLimitCooldown() {
        return rateLimitCooldown;
    }

    public Duration transientCooldown() {
        return transientCooldown;
    }

    public Duration invalidKeyCooldown() {
        return invalidKeyCooldown;
    }
}
