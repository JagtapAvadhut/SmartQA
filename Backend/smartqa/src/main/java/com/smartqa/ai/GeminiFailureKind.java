package com.smartqa.ai;

/**
 * Classified Gemini failure used for cooldown and rotation. Never includes secrets.
 */
public enum GeminiFailureKind {
    RATE_LIMITED,
    SERVER_ERROR,
    TIMEOUT,
    AUTH,
    SCHEMA,
    UNKNOWN
}
