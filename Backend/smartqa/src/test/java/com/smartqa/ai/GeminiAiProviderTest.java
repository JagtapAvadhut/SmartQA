package com.smartqa.ai;

import tools.jackson.databind.json.JsonMapper;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.rag.GeminiEmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class GeminiAiProviderTest {

    @Test
    void generateTextReadsCandidateText() {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"AI learns patterns.\"}]}}]}")
                .build());
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey("test-key");
        GeminiAiProvider provider = new GeminiAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);

        StepVerifier.create(provider.generateText(new AiPrompt("sys", "Explain how AI works in a few words.")))
                .expectNext("AI learns patterns.")
                .verifyComplete();
    }

    @Test
    void rotatesToNextKeyOnHttp429() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ExchangeFunction exchange = request -> {
            int n = calls.incrementAndGet();
            String key = request.headers().getFirst("x-goog-api-key");
            if (n == 1) {
                org.junit.jupiter.api.Assertions.assertEquals("key-a", key);
                return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"quota\"}")
                        .build());
            }
            org.junit.jupiter.api.Assertions.assertEquals("key-b", key);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}")
                    .build());
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey("key-a");
        properties.getAi().getGemini().setApiKeys("key-b");
        GeminiAiProvider provider = new GeminiAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);

        StepVerifier.create(provider.generateText(new AiPrompt("sys", "hi")))
                .expectNext("ok")
                .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals(2, calls.get());
    }

    @Test
    void pinsWorkingKeyForNextRequest() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ExchangeFunction exchange = request -> {
            int n = calls.incrementAndGet();
            String key = request.headers().getFirst("x-goog-api-key");
            if (n == 1) {
                org.junit.jupiter.api.Assertions.assertEquals("key-a", key);
                return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"bad key\"}")
                        .build());
            }
            org.junit.jupiter.api.Assertions.assertEquals("key-b", key);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}")
                    .build());
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey("key-a");
        properties.getAi().getGemini().setApiKeys("key-b");
        GeminiAiProvider provider = new GeminiAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);

        StepVerifier.create(provider.generateText(new AiPrompt("sys", "hi")))
                .expectNext("ok")
                .verifyComplete();
        StepVerifier.create(provider.generateText(new AiPrompt("sys", "again")))
                .expectNext("ok")
                .verifyComplete();
        // First call: key-a fail + key-b ok; second call starts on pinned key-b only.
        org.junit.jupiter.api.Assertions.assertEquals(3, calls.get());
    }

    @Test
    void missingApiKeyIsUnavailable() {
        GeminiAiProvider provider = new GeminiAiProvider(
                WebClient.builder().build(),
                JsonMapper.builder().build(),
                new SmartQaProperties());
        StepVerifier.create(provider.healthCheck())
                .expectNextMatches(health -> "UNAVAILABLE".equals(health.status()) && "NOT_CONFIGURED".equals(health.reason()))
                .verifyComplete();
    }

    @Test
    void multimodalPromptIncludesInlineImageData() {
        AiPrompt prompt = AiPrompt.json("sys", "user", List.of(AiMediaPart.png(new byte[] {1, 2, 3})));
        Map<String, Object> body = GeminiAiProvider.buildRequestBody(prompt);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contents.getFirst().get("parts");
        org.junit.jupiter.api.Assertions.assertEquals(2, parts.size());
        org.junit.jupiter.api.Assertions.assertTrue(parts.get(1).containsKey("inline_data"));
        org.junit.jupiter.api.Assertions.assertTrue(body.containsKey("generationConfig"));
    }

    @Test
    void rotatesOnHttp503AndTimeoutAndExhaustsToUnavailable() {
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"busy\"}")
                        .build());
            }
            return Mono.error(new TimeoutException("late"));
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey("key-a");
        properties.getAi().getGemini().setApiKeys("key-b");
        GeminiAiProvider provider = new GeminiAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);
        StepVerifier.create(provider.generateText(new AiPrompt("sys", "hi")))
                .expectErrorMatches(error -> error instanceof SmartQaException ex
                        && ex.errorCode() == ErrorCode.AI_UNAVAILABLE
                        && ex.getMessage().contains("GEMINI_POOL_EXHAUSTED"))
                .verify();
        org.junit.jupiter.api.Assertions.assertEquals(2, calls.get());
    }

    @Test
    void structuredAndMultimodalUseSameRotation() {
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"quota\"}")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"hello\\\":\\\"world\\\"}\"}]}}]}")
                    .build());
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey("key-a");
        properties.getAi().getGemini().setApiKeys("key-b");
        GeminiAiProvider provider = new GeminiAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);
        StepVerifier.create(provider.generateStructuredOutput(
                        AiPrompt.json("sys", "user", List.of(AiMediaPart.png(new byte[] {9}))), Sample.class))
                .expectNextMatches(sample -> "world".equals(sample.hello()))
                .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals(2, calls.get());
    }

    @Test
    void exhaustedGeminiFallsBackToOllama() {
        AtomicInteger geminiCalls = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            geminiCalls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"quota\"}")
                    .build());
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("gemini");
        properties.getAi().setFallbackProvider("ollama");
        properties.getAi().setMaxRetries(1);
        properties.getAi().getGemini().setApiKey("key-a");
        properties.getAi().getGemini().setApiKeys("key-b");
        GeminiAiProvider gemini = new GeminiAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);
        AiProvider ollama = new AiProvider() {
            @Override
            public String id() {
                return "ollama";
            }

            @Override
            public Mono<String> generateText(AiPrompt prompt) {
                return Mono.just("from-ollama");
            }

            @Override
            public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
                return Mono.error(new UnsupportedOperationException());
            }

            @Override
            public Mono<AiHealthStatus> healthCheck() {
                return Mono.just(AiHealthStatus.available("ollama", "m", "localhost", 1));
            }
        };
        FallbackAiProvider router = new FallbackAiProvider(Map.of("gemini", gemini, "ollama", ollama), properties);
        StepVerifier.create(router.generateText(new AiPrompt("sys", "hi")))
                .expectNext("from-ollama")
                .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals(2, geminiCalls.get());
    }

    @Test
    void healthCountsNeverIncludeSecretsAndSharedPoolServesEmbeddings() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> embedKey = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            String path = request.url().toString();
            String key = request.headers().getFirst("x-goog-api-key");
            if (path.contains("embedContent")) {
                embedKey.set(key);
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"embedding\":{\"values\":[0.1,0.2]}}")
                        .build());
            }
            int n = calls.incrementAndGet();
            if (n == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"quota\"}")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}")
                    .build());
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getGemini().setApiKey("secret-alpha");
        properties.getAi().getGemini().setApiKeys("secret-beta");
        properties.getRag().setEmbeddingDimension(2);
        WebClient client = WebClient.builder().exchangeFunction(exchange).build();
        GeminiKeyPool pool = new GeminiKeyPool(properties);
        GeminiAiProvider gemini = new GeminiAiProvider(client, JsonMapper.builder().build(), properties, pool);
        GeminiEmbeddingProvider embeddings = new GeminiEmbeddingProvider(
                client, JsonMapper.builder().build(), properties, pool);

        StepVerifier.create(gemini.generateText(new AiPrompt("sys", "hi")))
                .expectNext("ok")
                .verifyComplete();
        StepVerifier.create(embeddings.embed("hello"))
                .expectNextMatches(vector -> vector.length == 2)
                .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals("secret-beta", embedKey.get());
        org.junit.jupiter.api.Assertions.assertEquals(2, calls.get());

        AiHealthStatus health = gemini.healthCheck().block();
        org.junit.jupiter.api.Assertions.assertNotNull(health);
        org.junit.jupiter.api.Assertions.assertEquals(2, health.configuredKeys());
        org.junit.jupiter.api.Assertions.assertTrue(health.cooldownKeys() >= 1);
        String json = JsonMapper.builder().build().writeValueAsString(health);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("secret-alpha"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("secret-beta"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("configuredKeys"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("healthyKeys"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("cooldownKeys"));
    }

    private record Sample(String hello) {
    }
}
