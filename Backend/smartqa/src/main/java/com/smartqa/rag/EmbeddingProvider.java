package com.smartqa.rag;

import reactor.core.publisher.Mono;

/**
 * Produces dense embeddings for RAG. Must not be a chat-generation model.
 */
public interface EmbeddingProvider {

    String id();

    String model();

    /** Expected output dimension for the configured model. */
    int dimension();

    Mono<float[]> embed(String text);

    Mono<Boolean> available();
}
