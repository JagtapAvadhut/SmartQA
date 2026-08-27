package com.smartqa.health;

public record GenerationHealthResponse(
        String status,
        String application,
        boolean generationAvailable,
        String browserEngine,
        String executionProvider
) {
}
