package com.smartqa.rag;

import com.smartqa.ai.GeminiKeyPool;
import com.smartqa.common.config.SmartQaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RagEmbeddingConfig {

    @Bean
    public OllamaEmbeddingProvider ollamaEmbeddingProvider(
            WebClient aiWebClient, JsonMapper jsonMapper, SmartQaProperties properties) {
        return new OllamaEmbeddingProvider(aiWebClient, jsonMapper, properties);
    }

    @Bean
    public GeminiEmbeddingProvider geminiEmbeddingProvider(
            WebClient aiWebClient, JsonMapper jsonMapper, SmartQaProperties properties, GeminiKeyPool geminiKeyPool) {
        return new GeminiEmbeddingProvider(aiWebClient, jsonMapper, properties, geminiKeyPool);
    }

    @Bean
    @Primary
    public EmbeddingProvider embeddingProvider(
            OllamaEmbeddingProvider ollamaEmbeddingProvider,
            GeminiEmbeddingProvider geminiEmbeddingProvider,
            SmartQaProperties properties) {
        return new FallbackEmbeddingProvider(ollamaEmbeddingProvider, geminiEmbeddingProvider, properties);
    }
}
