package com.smartqa.rag;

import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.AiProvider;
import com.smartqa.ai.FallbackAiProvider;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.pipeline.AiFailureDiagnosticService;
import com.smartqa.pipeline.AssertionFailureAnalyzer;
import com.smartqa.pipeline.FailureEvidence;
import com.smartqa.pipeline.FilterFailureAnalyzer;
import com.smartqa.pipeline.GenericFailurePatternStore;
import com.smartqa.pipeline.SearchFailureAnalyzer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAiDiagnosticIntegrationTest {

    @Test
    void vectorRagInjectedIntoAiPromptButDoesNotOverrideLiveDomPriority() {
        AtomicReference<String> capturedUser = new AtomicReference<>();
        JsonMapper mapper = JsonMapper.builder().build();
        AiProvider gemini = new AiProvider() {
            @Override
            public String id() {
                return "gemini";
            }

            @Override
            public Mono<String> generateText(AiPrompt prompt) {
                capturedUser.set(prompt.user());
                return Mono.just("{}");
            }

            @Override
            public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
                capturedUser.set(prompt.user());
                return Mono.fromCallable(() -> mapper.readValue("""
                        {"classification":"AMBIGUOUS_ELEMENT","confidence":0.8,
                         "explanation":"Prefer account semantics","recommendedCandidateId":"candidate-B",
                         "recoveryOptions":[{"type":"RE_RESOLVE","reason":"profile","safe":true,"confidence":0.8}]}
                        """, type));
            }

            @Override
            public Mono<com.smartqa.ai.AiHealthStatus> healthCheck() {
                return Mono.just(com.smartqa.ai.AiHealthStatus.available("gemini", "test", "local", 1));
            }
        };

        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("gemini");
        properties.getAi().setConsensusEnabled(false);
        properties.getAi().getGemini().setApiKey("test-key");
        properties.getRag().setEnabled(true);
        FallbackAiProvider fallback = new FallbackAiProvider(Map.of("gemini", gemini), properties);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setScope(KnowledgeScope.GLOBAL_GENERIC);
        doc.setContentType(KnowledgeContentType.LOCATOR_PATTERN);
        doc.setContent("Account/profile controls often expose person/account semantics.");
        doc.setSimilarity(0.91);
        RagRetrievalResult ragResult = new RagRetrievalResult(
                List.of(doc), List.of(), 1, 0.91, 12, "ollama", "nomic-embed-text", "profile icon");

        RagRetrievalService ragStub = new RagRetrievalService(
                new EmbeddingProvider() {
                    @Override public String id() { return "stub"; }
                    @Override public String model() { return "stub"; }
                    @Override public int dimension() { return 768; }
                    @Override public Mono<float[]> embed(String text) { return Mono.empty(); }
                    @Override public Mono<Boolean> available() { return Mono.just(true); }
                },
                null,
                properties) {
            @Override
            public Mono<RagRetrievalResult> retrieveForFailure(FailureEvidence evidence) {
                return Mono.just(ragResult);
            }
        };

        AiFailureDiagnosticService service = new AiFailureDiagnosticService(
                fallback, properties,
                new AssertionFailureAnalyzer(),
                new SearchFailureAnalyzer(),
                new FilterFailureAnalyzer(),
                new GenericFailurePatternStore(),
                ragStub,
                null);

        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://www.urbancompany.com/pune")
                .instruction("Click profile icon")
                .action("click")
                .target("profile")
                .candidateLocators(List.of("cart", "profile"))
                .candidateScores(List.of(88.0, 86.0))
                .domExcerpt("button[aria-label=Account] button[aria-label=Cart]")
                .confidence(0.62)
                .build();

        StepVerifier.create(service.diagnose(evidence, "LOCATOR", 1))
                .assertNext(result -> {
                    assertTrue(result.confidence() >= 0.7);
                    String user = capturedUser.get();
                    assertTrue(user != null && (user.contains("Vector RAG") || user.contains("person/account")));
                    assertTrue(user.contains("live DOM") || user.contains("Fresh multimodal"));
                    assertFalse(user.contains("Use selector .fk-xyz"));
                })
                .verifyComplete();
    }

    @Test
    void advisoryPromptDeclaresLiveDomPrecedence() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setScope(KnowledgeScope.GLOBAL_GENERIC);
        doc.setContentType(KnowledgeContentType.FILTER_PATTERN);
        doc.setContent("Filters may use accordion + checkbox.");
        doc.setSimilarity(0.88);
        String block = new RagRetrievalResult(List.of(doc), List.of(), 1, 0.88, 5, "ollama", "nomic-embed-text", "filter")
                .toAdvisoryPromptBlock();
        assertTrue(block.contains("CURRENT LIVE DOM"));
        assertTrue(block.contains("advisory"));
    }
}
