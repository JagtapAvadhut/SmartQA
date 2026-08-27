package com.smartqa.rag;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.pipeline.FailureEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Ollama + pgvector integration. Enable with SMARTQA_RAG_IT=true.
 */
@EnabledIfEnvironmentVariable(named = "SMARTQA_RAG_IT", matches = "true")
class RagPgVectorIntegrationTest {

    @Test
    void embedInsertSearchScopeIsolationAndRelevanceGate() {
        SmartQaProperties props = new SmartQaProperties();
        props.getRag().setEnabled(true);
        props.getRag().setEmbeddingProvider("ollama");
        props.getRag().setOllamaEmbeddingModel("nomic-embed-text");
        props.getRag().setEmbeddingDimension(768);
        props.getRag().setTopK(5);
        props.getRag().setRelevanceThreshold(0.55);

        JsonMapper mapper = JsonMapper.builder().build();
        var webClient = org.springframework.web.reactive.function.client.WebClient.builder().build();
        OllamaEmbeddingProvider embedder = new OllamaEmbeddingProvider(webClient, mapper, props);

        ConnectionFactory cf = ConnectionFactories.get(
                "r2dbc:postgresql://postgres:postgres@localhost:5432/smartqa");
        DatabaseClient db = DatabaseClient.create(cf);
        // Isolate from prior IT debris
        db.sql("DELETE FROM smartqa_knowledge WHERE source = 'test' OR content LIKE 'UNRELATED_ASTRONOMY%' OR content LIKE 'FILTER_PATTERN rag-it-%'")
                .fetch().rowsUpdated().block(Duration.ofSeconds(10));

        RagKnowledgeRepository repo = new RagKnowledgeRepository(db);
        RagIngestionService ingest = new RagIngestionService(embedder, repo, props);
        RagRetrievalService retrieval = new RagRetrievalService(embedder, repo, props);

        String unique = "rag-it-" + UUID.randomUUID();
        String astroMarker = "astro-" + UUID.randomUUID();
        String filterContent = "FILTER_PATTERN " + unique
                + ": Filters may be implemented as accordion + checkbox + chip; expand closed panels.";
        String irrelevant = "UNRELATED_ASTRONOMY " + astroMarker
                + ": Spiral galaxies contain billions of stars and dark matter halos.";

        StepVerifier.create(
                        embedder.embed("probe dimension")
                                .doOnNext(v -> assertEquals(768, v.length))
                                .then(ingest.ingest(KnowledgeScope.GLOBAL_GENERIC, "global",
                                        KnowledgeContentType.FILTER_PATTERN, filterContent, "test", null, null))
                                .then(ingest.ingest(KnowledgeScope.APPLICATION, "flipkart.com",
                                        KnowledgeContentType.APPLICATION_PATTERN,
                                        "APPLICATION_PATTERN " + unique
                                                + ": historical app hint only, still verify live DOM.",
                                        "test", null, null))
                                .then(ingest.ingest(KnowledgeScope.GLOBAL_GENERIC, "global",
                                        KnowledgeContentType.GENERIC_BROWSER_PATTERN, irrelevant, "test", null, null))
                                .then(retrieval.retrieve(RagRetrievalRequest.builder()
                                        .query("FILTER_NOT_OPEN brand filter accordion checkbox chip " + unique)
                                        .applicationKey("flipkart.com")
                                        .failureCategory("FILTER_NOT_OPEN")
                                        .topK(5)
                                        .build()))
                )
                .assertNext(result -> {
                    assertTrue(result.retrievedCount() >= 1, "expected vector hits");
                    assertTrue(result.topScore() > 0.0 || !result.accepted().isEmpty());
                    boolean hasFilter = result.accepted().stream()
                            .anyMatch(d -> d.getContent() != null && d.getContent().contains("FILTER_PATTERN"));
                    boolean leakedOtherApp = result.accepted().stream()
                            .anyMatch(d -> d.getScope() == KnowledgeScope.APPLICATION
                                    && !"flipkart.com".equals(d.getScopeKey()));
                    assertFalse(leakedOtherApp);
                    boolean astronomyAccepted = result.accepted().stream()
                            .anyMatch(d -> d.getContent() != null && d.getContent().contains(astroMarker));
                    assertFalse(astronomyAccepted, "irrelevant astronomy memory must fail relevance gate");
                    assertTrue(hasFilter, "filter pattern should be retrieved for FILTER_NOT_OPEN");
                })
                .expectComplete()
                .verify(Duration.ofMinutes(2));

        StepVerifier.create(retrieval.retrieve(RagRetrievalRequest.builder()
                        .query("profile icon account semantics " + unique)
                        .applicationKey("urbancompany.com")
                        .failureCategory("AMBIGUOUS_ELEMENT")
                        .topK(5)
                        .build()))
                .assertNext(result -> {
                    boolean flipkartApp = result.accepted().stream()
                            .anyMatch(d -> d.getScope() == KnowledgeScope.APPLICATION
                                    && "flipkart.com".equals(d.getScopeKey()));
                    assertFalse(flipkartApp, "application memories must stay isolated");
                })
                .expectComplete()
                .verify(Duration.ofMinutes(2));

        StepVerifier.create(ingest.ingest(KnowledgeScope.GLOBAL_GENERIC, "global",
                        KnowledgeContentType.RECOVERY_PATTERN,
                        "password=admin123 api_key=sk-secret-token-value",
                        "test", null, null))
                .verifyComplete();

        assertEquals("example.com", RagRetrievalService.applicationKeyFrom("https://www.example.com/path"));
        assertTrue(RagRetrievalService.buildFailureQuery(FailureEvidence.builder()
                .failureCategory("FILTER_NOT_OPEN")
                .action("click")
                .target("Brand")
                .build()).contains("FILTER_NOT_OPEN"));
    }
}
