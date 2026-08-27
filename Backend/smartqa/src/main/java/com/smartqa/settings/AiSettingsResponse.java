package com.smartqa.settings;

public record AiSettingsResponse(
        String provider,
        String primaryProvider,
        String fallbackProvider,
        String ollamaBaseUrl,
        String ollamaModel,
        String geminiModel,
        boolean geminiConfigured,
        String openaiCompatibleBaseUrl,
        String openaiCompatibleModel,
        boolean openaiCompatibleConfigured,
        int timeoutSeconds
) {
}
