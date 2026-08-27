package com.smartqa.health;

import com.smartqa.ai.AiHealthSnapshot;
import com.smartqa.ai.FallbackAiProvider;
import com.smartqa.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AiHealthController {

    private final FallbackAiProvider fallbackAiProvider;

    public AiHealthController(FallbackAiProvider fallbackAiProvider) {
        this.fallbackAiProvider = fallbackAiProvider;
    }

    @GetMapping("/api/health/ai")
    public Mono<ApiResponse<AiHealthSnapshot>> ai() {
        return fallbackAiProvider.healthAll()
                .map(snapshot -> ApiResponse.ok("AI provider health", snapshot));
    }
}
