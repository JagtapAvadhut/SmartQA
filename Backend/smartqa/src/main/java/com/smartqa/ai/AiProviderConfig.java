package com.smartqa.ai;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.event.ProgressEventHub;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

@Configuration
public class AiProviderConfig {

    @Bean
    public WebClient aiWebClient(SmartQaProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, AiCalls.connectTimeoutMillis(properties))
                .responseTimeout(Duration.ofSeconds(AiCalls.timeoutSeconds(properties)));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public GeminiKeyPool geminiKeyPool(SmartQaProperties properties) {
        return new GeminiKeyPool(properties);
    }

    @Bean
    public OllamaAiProvider ollamaAiProvider(WebClient aiWebClient, JsonMapper jsonMapper, SmartQaProperties properties) {
        return new OllamaAiProvider(aiWebClient, jsonMapper, properties);
    }

    @Bean
    public GeminiAiProvider geminiAiProvider(
            WebClient aiWebClient, JsonMapper jsonMapper, SmartQaProperties properties, GeminiKeyPool geminiKeyPool) {
        return new GeminiAiProvider(aiWebClient, jsonMapper, properties, geminiKeyPool);
    }

    @Bean
    public OpenAiCompatibleAiProvider openAiCompatibleAiProvider(
            WebClient aiWebClient, JsonMapper jsonMapper, SmartQaProperties properties) {
        return new OpenAiCompatibleAiProvider(aiWebClient, jsonMapper, properties);
    }

    @Bean
    @Primary
    public FallbackAiProvider aiProvider(
            OllamaAiProvider ollamaAiProvider,
            GeminiAiProvider geminiAiProvider,
            OpenAiCompatibleAiProvider openAiCompatibleAiProvider,
            SmartQaProperties properties,
            ProgressEventHub eventHub) {
        return new FallbackAiProvider(
                Map.of(
                        "ollama", ollamaAiProvider,
                        "gemini", geminiAiProvider,
                        "openai", openAiCompatibleAiProvider,
                        "openai-compatible", openAiCompatibleAiProvider
                ),
                properties,
                eventHub);
    }
}
