package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

class FallbackAiProviderTest {

    @Test
    void usesFallbackWhenPrimaryTimesOut() {
        AiProvider primary = provider("ollama", Mono.error(new SmartQaException(
                ErrorCode.AI_TIMEOUT, "AI provider did not respond within 1 seconds.")));
        AiProvider fallback = provider("gemini", Mono.just("{\"ok\":true}"));
        FallbackAiProvider router = router(primary, fallback);

        StepVerifier.create(router.generateText(new AiPrompt("sys", "user")))
                .expectNext("{\"ok\":true}")
                .verifyComplete();
    }

    @Test
    void coldPrimaryIsUsableAndReceivesRequest() {
        AiProvider primary = new RecordingProvider("ollama") {
            @Override
            public Mono<AiHealthStatus> healthCheck() {
                return Mono.just(AiHealthStatus.cold("ollama", "qwen", "localhost", "MODEL_NOT_LOADED", 5));
            }

            @Override
            public Mono<String> generateText(AiPrompt prompt) {
                calls += 1;
                return Mono.just("cold-start-ok");
            }
        };
        AiProvider fallback = provider("gemini", Mono.just("fast"));
        FallbackAiProvider router = router(primary, fallback);

        StepVerifier.create(router.generateText(new AiPrompt("sys", "user")))
                .expectNext("cold-start-ok")
                .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals(1, ((RecordingProvider) primary).calls);
    }

    @Test
    void bothFailReturnProvidersUnavailable() {
        AiProvider primary = provider("ollama", Mono.error(new SmartQaException(ErrorCode.AI_TIMEOUT, "timeout")));
        AiProvider fallback = provider("gemini", Mono.error(new SmartQaException(
                ErrorCode.AI_PROVIDER_ERROR, "GEMINI_API_KEY is not configured")));
        FallbackAiProvider router = router(primary, fallback);

        StepVerifier.create(router.generateText(new AiPrompt("sys", "user")))
                .expectErrorMatches(error -> error instanceof SmartQaException ex
                        && ex.errorCode() == ErrorCode.AI_PROVIDERS_UNAVAILABLE
                        && ex.getMessage().contains("primary=ollama")
                        && ex.getMessage().contains("fallback=gemini"))
                .verify();
    }

    @Test
    void invalidStructuredOutputFallsBack() {
        AiProvider primary = new RecordingProvider("ollama") {
            @Override
            public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
                return Mono.error(new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "bad json"));
            }
        };
        AiProvider fallback = new RecordingProvider("gemini") {
            @Override
            public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
                return Mono.just(type.cast(new Sample("world")));
            }
        };
        FallbackAiProvider router = router(primary, fallback);

        StepVerifier.create(router.generateStructuredOutput(AiPrompt.json("sys", "user"), Sample.class))
                .expectNextMatches(sample -> "world".equals(sample.hello()))
                .verifyComplete();
    }

    private static FallbackAiProvider router(AiProvider primary, AiProvider fallback) {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("ollama");
        properties.getAi().setFallbackProvider("gemini");
        properties.getAi().getGemini().setApiKey("test-key");
        properties.getAi().setTimeoutSeconds(1);
        return new FallbackAiProvider(Map.of("ollama", primary, "gemini", fallback), properties);
    }

    @Test
    void remapsPrimaryToOllamaWhenGeminiKeyMissing() {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("gemini");
        properties.getAi().setFallbackProvider("ollama");
        properties.getAi().getGemini().setApiKey("");
        FallbackAiProvider router = new FallbackAiProvider(
                Map.of("ollama", provider("ollama", Mono.just("ok")), "gemini", provider("gemini", Mono.just("no"))),
                properties);
        org.junit.jupiter.api.Assertions.assertEquals("ollama", router.primaryName());
        org.junit.jupiter.api.Assertions.assertEquals("", router.fallbackName());
    }

    @Test
    void keepsGeminiPrimaryWhenOnlyApiKeysConfigured() {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("gemini");
        properties.getAi().setFallbackProvider("ollama");
        properties.getAi().getGemini().setApiKey("");
        properties.getAi().getGemini().setApiKeys("csv-only-key");
        FallbackAiProvider router = new FallbackAiProvider(
                Map.of("ollama", provider("ollama", Mono.just("ok")), "gemini", provider("gemini", Mono.just("g"))),
                properties);
        org.junit.jupiter.api.Assertions.assertEquals("gemini", router.primaryName());
        org.junit.jupiter.api.Assertions.assertEquals("ollama", router.fallbackName());
    }

    @Test
    void generateStructuredDualReturnsSecondOpinion() {
        AiProvider primary = new RecordingProvider("gemini") {
            @Override
            public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
                return Mono.just(type.cast(new Sample("primary")));
            }
        };
        AiProvider secondary = new RecordingProvider("ollama") {
            @Override
            public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
                return Mono.just(type.cast(new Sample("secondary")));
            }
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("gemini");
        properties.getAi().setFallbackProvider("ollama");
        properties.getAi().getGemini().setApiKey("test-key");
        FallbackAiProvider router = new FallbackAiProvider(
                Map.of("gemini", primary, "ollama", secondary), properties);

        StepVerifier.create(router.generateStructuredDual(AiPrompt.json("sys", "user"), Sample.class))
                .assertNext(dual -> {
                    org.junit.jupiter.api.Assertions.assertEquals("primary", dual.primary().hello());
                    org.junit.jupiter.api.Assertions.assertEquals("secondary", dual.secondary().hello());
                    org.junit.jupiter.api.Assertions.assertTrue(dual.hasSecondOpinion());
                    org.junit.jupiter.api.Assertions.assertTrue(dual.dualAttempted());
                })
                .verifyComplete();
    }

    private static AiProvider provider(String id, Mono<String> response) {
        return new RecordingProvider(id) {
            @Override
            public Mono<String> generateText(AiPrompt prompt) {
                return response;
            }
        };
    }

    private record Sample(String hello) {
    }

    private static class RecordingProvider implements AiProvider {
        private final String id;
        int calls;

        RecordingProvider(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Mono<String> generateText(AiPrompt prompt) {
            calls += 1;
            return Mono.just("ok");
        }

        @Override
        public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
            return generateText(prompt).map(ignored -> {
                throw new UnsupportedOperationException();
            });
        }

        @Override
        public Mono<AiHealthStatus> healthCheck() {
            return Mono.just(AiHealthStatus.available(id, "model", "localhost", 1));
        }
    }
}
