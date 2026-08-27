package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

class Gemini429FallbackTest {

    @Test
    void geminiHttp429FallsBackToOllama() {
        AiProvider gemini = new FallbackAiProviderTestSupport("gemini", Mono.error(new SmartQaException(
                ErrorCode.AI_PROVIDER_ERROR, "gemini HTTP 429")));
        AiProvider ollama = new FallbackAiProviderTestSupport("ollama", Mono.just("{\"ok\":true}"));
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("gemini");
        properties.getAi().setFallbackProvider("ollama");
        properties.getAi().getGemini().setApiKey("test-key");
        properties.getAi().setMaxRetries(0);
        FallbackAiProvider router = new FallbackAiProvider(Map.of("gemini", gemini, "ollama", ollama), properties);

        StepVerifier.create(router.generateText(new AiPrompt("sys", "user")))
                .expectNext("{\"ok\":true}")
                .verifyComplete();
    }

    private static final class FallbackAiProviderTestSupport implements AiProvider {
        private final String id;
        private final Mono<String> response;

        private FallbackAiProviderTestSupport(String id, Mono<String> response) {
            this.id = id;
            this.response = response;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Mono<String> generateText(AiPrompt prompt) {
            return response;
        }

        @Override
        public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
            return generateText(prompt).map(ignored -> {
                throw new UnsupportedOperationException();
            });
        }

        @Override
        public Mono<AiHealthStatus> healthCheck() {
            return Mono.just(AiHealthStatus.available(id, "m", "localhost", 1));
        }
    }
}
