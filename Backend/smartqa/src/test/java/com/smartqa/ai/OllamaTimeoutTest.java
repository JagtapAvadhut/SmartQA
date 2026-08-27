package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class OllamaTimeoutTest {

    @Test
    void timeoutIsBoundedFailureNotHang() {
        ExchangeFunction exchange = request -> Mono.never();
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setTimeoutSeconds(1);
        OllamaAiProvider provider = new OllamaAiProvider(
                WebClient.builder().exchangeFunction(exchange).build(),
                JsonMapper.builder().build(),
                properties);
        StepVerifier.create(provider.generateText(new AiPrompt("sys", "user")))
                .expectErrorMatches(error -> error instanceof com.smartqa.common.error.SmartQaException ex
                        && ex.errorCode() == ErrorCode.AI_TIMEOUT)
                .verify(java.time.Duration.ofSeconds(4));
    }
}
