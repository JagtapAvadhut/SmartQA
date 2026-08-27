package com.smartqa.ai;

import reactor.core.publisher.Mono;

public interface AiProvider {

    String id();

    Mono<String> generateText(AiPrompt prompt);

    <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type);

    default Mono<AiHealthStatus> healthCheck() {
        return Mono.just(AiHealthStatus.unavailable(id(), null, null, "NOT_IMPLEMENTED", 0));
    }
}
