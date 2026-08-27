package com.smartqa.pipeline;

import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.FallbackAiProvider;
import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiFailureDiagnosticServiceTest {

    @Test
    void shouldCallAiForAmbiguousProfileEvidence() {
        AiFailureDiagnosticService service = service(Map.of());
        FailureEvidence evidence = FailureEvidence.builder()
                .action("click")
                .target("profile icon")
                .candidateLocators(List.of("cart", "profile"))
                .candidateScores(List.of(90.0, 88.0))
                .confidence(0.62)
                .build();
        assertTrue(service.shouldCallAi("LOCATOR", evidence, 1));
    }

    @Test
    void multimodalConsensusPathMergesOpinions() {
        AtomicInteger calls = new AtomicInteger();
        com.smartqa.ai.AiProvider gemini = recording("gemini", () -> {
            calls.incrementAndGet();
            AiDiagnosticResult r = new AiDiagnosticResult();
            r.setClassification("AMBIGUOUS_ELEMENT");
            r.setConfidence(0.82);
            r.setRecommendedCandidateId("candidate-B");
            r.setExplanation("Profile semantics");
            RecoveryOption opt = new RecoveryOption("RE_RESOLVE", "pick profile", true);
            opt.setConfidence(0.82);
            r.setRecoveryOptions(List.of(opt));
            return r;
        });
        com.smartqa.ai.AiProvider ollama = recording("ollama", () -> {
            calls.incrementAndGet();
            AiDiagnosticResult r = new AiDiagnosticResult();
            r.setClassification("AMBIGUOUS_TARGET");
            r.setConfidence(0.78);
            r.setRecommendedCandidateId("candidate-B");
            r.setExplanation("Account icon");
            RecoveryOption opt = new RecoveryOption("REDISCOVER_ELEMENT", "pick profile", true);
            opt.setConfidence(0.78);
            r.setRecoveryOptions(List.of(opt));
            return r;
        });
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("gemini");
        properties.getAi().setFallbackProvider("ollama");
        properties.getAi().setConsensusEnabled(true);
        properties.getAi().getGemini().setApiKey("test-key");
        FallbackAiProvider fallback = new FallbackAiProvider(Map.of("gemini", gemini, "ollama", ollama), properties);

        AiFailureDiagnosticService service = new AiFailureDiagnosticService(
                fallback,
                properties,
                new AssertionFailureAnalyzer(),
                new SearchFailureAnalyzer(),
                new FilterFailureAnalyzer(),
                new GenericFailurePatternStore(),
                null,
                null);

        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://www.urbancompany.com/pune")
                .instruction("Click profile icon")
                .action("click")
                .target("profile")
                .candidateLocators(List.of("cart", "profile"))
                .candidateScores(List.of(88.0, 86.0))
                .domExcerpt("<button>cart</button><button>profile</button>")
                .failureCategory("AMBIGUOUS_TARGET")
                .build();

        StepVerifier.create(service.diagnose(evidence, "AMBIGUOUS_TARGET", 1))
                .assertNext(result -> {
                    assertEquals("AMBIGUOUS_ELEMENT", result.normalizedClassification());
                    assertTrue(result.confidence() >= 0.82);
                    assertEquals("candidate-B", result.recommendedCandidateId());
                })
                .verifyComplete();
        assertTrue(calls.get() >= 2, "consensus should invoke both providers");
    }

    private static AiFailureDiagnosticService service(Map<String, com.smartqa.ai.AiProvider> unused) {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getAi().setProvider("ollama");
        properties.getAi().setFallbackProvider("");
        FallbackAiProvider fallback = new FallbackAiProvider(
                Map.of("ollama", recording("ollama", () -> AiDiagnosticResult.fallback("UNKNOWN", "x", "y", 0.5))),
                properties);
        return new AiFailureDiagnosticService(
                fallback,
                properties,
                new AssertionFailureAnalyzer(),
                new SearchFailureAnalyzer(),
                new FilterFailureAnalyzer(),
                new GenericFailurePatternStore(),
                null,
                null);
    }

    private static com.smartqa.ai.AiProvider recording(String id, java.util.function.Supplier<AiDiagnosticResult> supplier) {
        return new com.smartqa.ai.AiProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Mono<String> generateText(AiPrompt prompt) {
                return Mono.just("{}");
            }

            @Override
            public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
                return Mono.just(type.cast(supplier.get()));
            }

            @Override
            public Mono<com.smartqa.ai.AiHealthStatus> healthCheck() {
                return Mono.just(com.smartqa.ai.AiHealthStatus.available(id, "m", "localhost", 1));
            }
        };
    }
}
