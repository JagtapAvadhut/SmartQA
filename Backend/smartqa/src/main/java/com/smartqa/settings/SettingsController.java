package com.smartqa.settings;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.common.config.SmartQaProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class SettingsController {

    private final SmartQaProperties properties;

    public SettingsController(SmartQaProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/settings/ai")
    public Mono<ApiResponse<AiSettingsResponse>> ai() {
        var ai = properties.getAi();
        boolean geminiConfigured = !ai.getGemini().resolvedApiKeys().isEmpty();
        boolean openaiConfigured = ai.getOpenaiCompatible().getApiKey() != null
                && !ai.getOpenaiCompatible().getApiKey().isBlank();
        String primary = ai.getPrimaryProvider();
        return Mono.just(ApiResponse.ok("AI settings fetched", new AiSettingsResponse(
                primary,
                primary,
                ai.getFallbackProvider(),
                ai.getOllama().getBaseUrl(),
                ai.getOllama().getModel(),
                ai.getGemini().getModel(),
                geminiConfigured,
                ai.getOpenaiCompatible().getBaseUrl(),
                ai.getOpenaiCompatible().getModel(),
                openaiConfigured,
                ai.getTimeoutSeconds()
        )));
    }
}
