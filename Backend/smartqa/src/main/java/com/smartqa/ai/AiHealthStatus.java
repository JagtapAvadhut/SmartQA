package com.smartqa.ai;

public record AiHealthStatus(
        String provider,
        String model,
        String endpointHost,
        String status,
        String reason,
        long latencyMs,
        int configuredKeys,
        int healthyKeys,
        int cooldownKeys
) {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String COLD = "COLD";

    public boolean available() {
        return AVAILABLE.equals(status);
    }

    public boolean usable() {
        return AVAILABLE.equals(status) || COLD.equals(status);
    }

    public static AiHealthStatus available(String provider, String model, String endpointHost, long latencyMs) {
        return new AiHealthStatus(provider, model, endpointHost, AVAILABLE, null, latencyMs, 0, 0, 0);
    }

    public static AiHealthStatus unavailable(String provider, String model, String endpointHost, String reason, long latencyMs) {
        return new AiHealthStatus(provider, model, endpointHost, UNAVAILABLE, reason, latencyMs, 0, 0, 0);
    }

    public static AiHealthStatus cold(String provider, String model, String endpointHost, String reason, long latencyMs) {
        return new AiHealthStatus(provider, model, endpointHost, COLD, reason, latencyMs, 0, 0, 0);
    }

    public AiHealthStatus withKeyCounts(int configured, int healthy, int cooldown) {
        return new AiHealthStatus(
                provider, model, endpointHost, status, reason, latencyMs,
                Math.max(0, configured), Math.max(0, healthy), Math.max(0, cooldown));
    }
}
