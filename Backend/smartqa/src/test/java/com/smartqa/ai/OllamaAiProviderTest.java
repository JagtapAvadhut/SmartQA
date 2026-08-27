package com.smartqa.ai;

import tools.jackson.databind.json.JsonMapper;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaAiProviderTest {

    @Test
    void generateTextReadsMessageContent() {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}")
                .build());
        WebClient client = WebClient.builder().exchangeFunction(exchange).build();
        OllamaAiProvider provider = new OllamaAiProvider(client, JsonMapper.builder().build(), new SmartQaProperties());

        StepVerifier.create(provider.generateText(new AiPrompt("sys", "user")))
                .expectNext("{\"ok\":true}")
                .verifyComplete();
    }

    @Test
    void jsonPromptSetsFormatJson() {
        Map<String, Object> body = OllamaAiProvider.buildRequestBody(AiPrompt.json("sys", "user"), "qwen2.5-coder:7b");
        assertEquals("json", body.get("format"));
        assertEquals(false, body.get("stream"));
        assertEquals("qwen2.5-coder:7b", body.get("model"));
    }

    @Test
    void structuredOutputParsesJson() {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"message\":{\"content\":\"{\\\"hello\\\":\\\"world\\\"}\"}}")
                .build());
        WebClient client = WebClient.builder().exchangeFunction(exchange).build();
        OllamaAiProvider provider = new OllamaAiProvider(client, JsonMapper.builder().build(), new SmartQaProperties());

        StepVerifier.create(provider.generateStructuredOutput(AiPrompt.json("sys", "user"), Sample.class))
                .expectNextMatches(sample -> "world".equals(sample.hello()))
                .verifyComplete();
    }

    @Test
    void generateTextTimesOutInsteadOfHanging() {
        ExchangeFunction exchange = request -> Mono.never();
        WebClient client = WebClient.builder().exchangeFunction(exchange).build();
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setTimeoutSeconds(1);
        OllamaAiProvider provider = new OllamaAiProvider(client, JsonMapper.builder().build(), properties);

        StepVerifier.create(provider.generateText(new AiPrompt("sys", "user")))
                .expectErrorMatches(error -> error instanceof com.smartqa.common.error.SmartQaException ex
                        && ex.errorCode() == com.smartqa.common.error.ErrorCode.AI_TIMEOUT
                        && ex.getMessage().contains("within 1 seconds"))
                .verify(java.time.Duration.ofSeconds(4));
    }

    @Test
    void healthCheckReportsColdWhenModelIsNotLoaded() {
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            String body = path.endsWith("/api/ps")
                    ? "{\"models\":[]}"
                    : "{\"models\":[{\"name\":\"qwen2.5-coder:7b\"}]}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        };
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().getOllama().setModel("qwen2.5-coder:7b");
        OllamaAiProvider provider = new OllamaAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);
        StepVerifier.create(provider.healthCheck())
                .expectNextMatches(health -> "COLD".equals(health.status()) && "MODEL_NOT_LOADED".equals(health.reason()))
                .verifyComplete();
    }

    @Test
    void multimodalPromptIncludesImagesArray() {
        AiPrompt prompt = AiPrompt.json("sys", "click profile", List.of(AiMediaPart.png(new byte[] {9, 8, 7})));
        Map<String, Object> body = OllamaAiProvider.buildRequestBody(prompt, "llava");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        Map<String, Object> user = messages.get(1);
        org.junit.jupiter.api.Assertions.assertTrue(user.containsKey("images"));
        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) user.get("images");
        org.junit.jupiter.api.Assertions.assertEquals(1, images.size());
        org.junit.jupiter.api.Assertions.assertEquals("json", body.get("format"));
    }

    private record Sample(String hello) {
    }
}
