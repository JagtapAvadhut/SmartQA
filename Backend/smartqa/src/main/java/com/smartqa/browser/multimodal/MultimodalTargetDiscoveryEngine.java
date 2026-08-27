package com.smartqa.browser.multimodal;

import com.microsoft.playwright.Page;
import com.smartqa.ai.AiCalls;
import com.smartqa.ai.AiMediaPart;
import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.AiProvider;
import com.smartqa.ai.AiTelemetry;
import com.smartqa.ai.FallbackAiProvider;
import com.smartqa.browser.intelligence.BrowserSnapshot;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.BrowserEvidenceOrchestrator;
import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.LocatorRanker;
import com.smartqa.browser.intelligence.cdp.CdpCapture;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.json.JsonSupport;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.rag.RagRetrievalRequest;
import com.smartqa.rag.RagRetrievalResult;
import com.smartqa.rag.RagRetrievalService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Visual + DOM + AI target discovery. Gemini reasons; Safety Gate verifies; Playwright executes.
 */
@Component
public class MultimodalTargetDiscoveryEngine {

    private static final String SYSTEM = """
            You are SmartQA's multimodal target analyst. You receive a fresh screenshot and compact live DOM.
            Return STRICT JSON:
            {
              "classification": "FILTER_TARGET|GENERIC_TARGET|TARGET_NOT_PRESENT|VISUAL_TARGET_PRESENT_DOM_UNRESOLVED|VISUAL_TEXT_IN_IMAGE",
              "semanticField": "Brand",
              "targetValue": "AK",
              "recommendedCandidateId": "candidate-A",
              "recommendedStrategy": "resolve_child_of_filter_container",
              "confidence": 0.0,
              "visualTargetPresent": true,
              "domResolved": true,
              "reason": "...",
              "candidateEvidence": [{"description":"...","reason":"..."}]
            }
            Rules:
            - Prefer candidateId from the provided list.
            - For filter instructions, the option belongs to the named field container
              (Brand AK is the AK checkbox under Brand, not header/login/cart text).
            - Never invent CSS/XPath. Never return Playwright. Never use coordinates as execution truth.
            - If the screenshot shows the target but the candidate list does not, set VISUAL_TARGET_PRESENT_DOM_UNRESOLVED.
            - If the requested text is visible only inside an image, canvas, or banner and not as a DOM text node, set VISUAL_TEXT_IN_IMAGE.
            - If the target is truly absent after collapsed panels, iframes, shadow, overlay, and viewport, set TARGET_NOT_PRESENT.
            """;

    private final AiProvider aiProvider;
    private final JsonMapper objectMapper;
    private final BrowserIntelligenceService intelligence;
    private final SmartQaProperties properties;
    private final ObjectProvider<RagRetrievalService> ragProvider;
    private final ObjectProvider<BrowserEvidenceOrchestrator> evidenceOrchestrator;

    public MultimodalTargetDiscoveryEngine(
            AiProvider aiProvider,
            JsonMapper objectMapper,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties,
            ObjectProvider<RagRetrievalService> ragProvider) {
        this(aiProvider, objectMapper, intelligence, properties, ragProvider, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MultimodalTargetDiscoveryEngine(
            AiProvider aiProvider,
            JsonMapper objectMapper,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties,
            ObjectProvider<RagRetrievalService> ragProvider,
            ObjectProvider<BrowserEvidenceOrchestrator> evidenceOrchestrator) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.intelligence = intelligence;
        this.properties = properties;
        this.ragProvider = ragProvider;
        this.evidenceOrchestrator = evidenceOrchestrator;
    }

    public record DiscoveryResult(
            Optional<LocatorRanker.RankedElement> ranked,
            TargetHypothesis hypothesis,
            AbsenceDiagnosis absence,
            BrowserEvidenceBundle evidence,
            String trigger,
            boolean accepted
    ) {
    }

    public DiscoveryResult discover(
            Page page,
            String action,
            String target,
            BrowserSnapshot snapshot,
            List<LocatorRanker.RankedElement> ranked,
            String trigger,
            List<String> previousAttempts) {
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize(action, target);
        BrowserSnapshot live = snapshot;
        if (page != null) {
            try {
                live = intelligence.inspect(page, List.of());
            } catch (RuntimeException ignored) {
            }
        }
        List<ElementCandidate> elements = live == null || live.elements() == null ? List.of() : live.elements();
        List<LocatorRanker.RankedElement> working = ranked == null ? List.of() : ranked;
        if (intent.isFilterOption()) {
            List<LocatorRanker.RankedElement> owned = LocatorRanker.rankOwned(
                    elements, action, intent.value(), intent.semanticField(), null);
            if (!owned.isEmpty()) {
                working = owned;
            }
        }
        byte[] screenshot = captureScreenshot(page);
        CdpCapture cdpCapture = CdpCapture.unavailable("not_requested");
        String momentId = null;
        String cdpStatus = "unavailable";
        if (evidenceOrchestrator != null) {
            BrowserEvidenceOrchestrator orchestrator = evidenceOrchestrator.getIfAvailable();
            if (orchestrator != null) {
                BrowserEvidenceOrchestrator.EvidenceMoment moment = orchestrator.captureMoment(page, true);
                cdpCapture = moment.cdp();
                momentId = moment.momentId();
                cdpStatus = moment.cdpStatus();
            }
        }
        CandidateRelationshipGraph.Graph graph = CandidateRelationshipGraph.build(elements);
        int[] viewport = viewportOf(page);
        var regions = VisualRegionAnalyzer.assign(elements, viewport[0], viewport[1]);
        BrowserEvidenceBundle evidence = new BrowserEvidenceBundle(
                target,
                intent.ownedHint(),
                action,
                intent.semanticField(),
                intent.value(),
                live == null ? "" : live.url(),
                live == null ? "" : live.title(),
                viewport[0],
                viewport[1],
                screenshot,
                BrowserEvidenceBundle.compactDom(elements, intent.value(), intent.semanticField())
                        + (cdpCapture.captured() ? "\nCDP DOM:\n" + cdpCapture.compactDom(20)
                        + "\nAX:\n" + cdpCapture.compactAccessibility(16) : ""),
                graph.compact(24),
                VisualRegionAnalyzer.compact(regions, 24),
                summarizeFrames(elements),
                summarizeShadow(elements),
                working.size(),
                Instant.now(),
                momentId,
                cdpStatus,
                previousAttempts
        );
        TraceLogger.info("AI", "AI_ESCALATION_STARTED", "AI escalation started", TraceMeta.of(
                "trigger", trigger,
                "action", action,
                "target", target,
                "url", evidence.url(),
                "pageTitle", evidence.pageTitle(),
                "screenshotIncluded", evidence.screenshotIncluded(),
                "screenshotBytes", evidence.screenshotPng() == null ? 0 : evidence.screenshotPng().length,
                "evidenceSize", evidence.evidenceSize(),
                "domIncluded", evidence.relevantDom() != null && !evidence.relevantDom().isBlank(),
                "cdpIncluded", cdpCapture.captured(),
                "axIncluded", cdpCapture.captured(),
                "graphIncluded", true,
                "layoutIncluded", evidence.layoutRegions() != null && !evidence.layoutRegions().isBlank(),
                "candidateCount", working.size(),
                "momentId", momentId,
                "cdpStatus", cdpStatus
        ));
        TraceLogger.info("AI", "SCREENSHOT_CAPTURED", "Screenshot captured for AI", TraceMeta.of(
                "screenshotIncluded", evidence.screenshotIncluded(),
                "screenshotBytes", evidence.screenshotPng() == null ? 0 : evidence.screenshotPng().length,
                "url", evidence.url()
        ));
        TraceLogger.info("AI", "DOM_CAPTURED", "DOM captured for AI", TraceMeta.of(
                "domEvidenceSize", evidence.relevantDom() == null ? 0 : evidence.relevantDom().length(),
                "elementCount", elements.size()
        ));
        TraceLogger.info("AI", "CDP_CAPTURED", "CDP captured for AI", TraceMeta.of(
                "cdpCaptured", cdpCapture.captured(),
                "cdpNodeCount", cdpCapture.nodeCount(),
                "cdpStatus", cdpStatus
        ));
        TraceLogger.info("AI", "AX_CAPTURED", "Accessibility tree captured for AI", TraceMeta.of(
                "axIncluded", cdpCapture.captured(),
                "cdpStatus", cdpStatus
        ));
        TraceLogger.info("AI", "GRAPH_BUILT", "Candidate graph built", TraceMeta.of(
                "candidateCount", working.size(),
                "graphChars", evidence.relationshipGraph() == null ? 0 : evidence.relationshipGraph().length()
        ));
        TraceLogger.info("AI", "AI_EVIDENCE_PACKAGED", "Multimodal evidence packaged for the model", TraceMeta.of(
                "screenshotIncluded", evidence.screenshotIncluded(),
                "domIncluded", true,
                "cdpIncluded", cdpCapture.captured(),
                "axIncluded", cdpCapture.captured(),
                "graphIncluded", true,
                "evidenceSize", evidence.evidenceSize(),
                "provider", aiProvider.id()
        ));
        TraceLogger.info("AI", "MULTIMODAL_ESCALATION", "Multimodal target discovery started", TraceMeta.of(
                "trigger", trigger,
                "action", action,
                "target", target,
                "semanticField", intent.semanticField(),
                "value", intent.value(),
                "candidateCount", working.size(),
                "screenshotIncluded", evidence.screenshotIncluded(),
                "domEvidenceSize", evidence.relevantDom() == null ? 0 : evidence.relevantDom().length(),
                "cdpCaptured", cdpCapture.captured(),
                "cdpNodeCount", cdpCapture.nodeCount()
        ));
        String rag = retrieveRag(intent, trigger);
        TargetHypothesis hypothesis = askModels(evidence, working, rag, trigger);
        AbsenceDiagnosis absence = AbsenceDiagnosis.inspect(
                elements, working, intent, hypothesis, evidence.screenshotIncluded());
        TargetSafetyGate.GateResult gate = TargetSafetyGate.verify(hypothesis, working, intent);
        TraceLogger.info("AI", "AI_CLASSIFICATION", "AI classification", TraceMeta.of(
                "classification", hypothesis == null ? "" : hypothesis.classification(),
                "visualTargetPresent", hypothesis != null && hypothesis.visualTargetPresent(),
                "domResolved", hypothesis != null && hypothesis.domResolved(),
                "targetType", hypothesis == null ? "" : hypothesis.targetType(),
                "visibleText", hypothesis == null ? "" : hypothesis.visibleText(),
                "reason", hypothesis == null ? "" : hypothesis.reason()
        ));
        TraceLogger.info("AI", "AI_CONFIDENCE", "AI confidence", TraceMeta.of(
                "confidence", hypothesis == null ? 0 : hypothesis.confidence(),
                "recommendedCandidateId", hypothesis == null ? "" : hypothesis.recommendedCandidateId(),
                "strategy", hypothesis == null ? "" : hypothesis.recommendedStrategy()
        ));
        TraceLogger.info("AI", "SAFETY_GATE_RESULT", "Safety gate result", TraceMeta.of(
                "safetyGate", gate.outcome(),
                "accepted", gate.accepted(),
                "reason", gate.reason(),
                "rankedLocator", gate.ranked() == null || gate.ranked().element() == null
                        ? "" : gate.ranked().element().candidateId()
        ));
        TraceLogger.info("AI", "MULTIMODAL_ESCALATION_COMPLETE", "Multimodal discovery finished", TraceMeta.of(
                "trigger", trigger,
                "classification", hypothesis == null ? "" : hypothesis.classification(),
                "confidence", hypothesis == null ? 0 : hypothesis.confidence(),
                "strategy", hypothesis == null ? "" : hypothesis.recommendedStrategy(),
                "safetyGate", gate.outcome(),
                "accepted", gate.accepted()
        ));
        if (gate.accepted()) {
            return new DiscoveryResult(Optional.of(gate.ranked()), hypothesis, absence, evidence, trigger, true);
        }
        return new DiscoveryResult(Optional.empty(), hypothesis, absence, evidence, trigger, false);
    }

    private TargetHypothesis askModels(
            BrowserEvidenceBundle evidence,
            List<LocatorRanker.RankedElement> ranked,
            String rag,
            String trigger) {
        String user = evidence.toPromptText()
                + (rag == null || rag.isBlank() ? "" : "\nRAG (advisory only):\n" + rag)
                + "\nIndexed candidates:\n" + formatCandidates(ranked);
        List<AiMediaPart> media = evidence.screenshotIncluded()
                ? List.of(EvidenceImageCompressor.compact(evidence.screenshotPng()))
                : List.of();
        AiPrompt prompt = AiPrompt.json(SYSTEM, user, media);
        long started = System.currentTimeMillis();
        boolean cdpIncluded = evidence.cdpStatus() != null && !"unavailable".equals(evidence.cdpStatus());
        int keyCount = properties.getAi().getGemini().resolvedApiKeys().size();
        AiTelemetry.callStarted(
                trigger,
                aiProvider.id(),
                "",
                evidence.evidenceSize(),
                evidence.screenshotIncluded(),
                true,
                cdpIncluded,
                cdpIncluded,
                true,
                0,
                keyCount);
        TraceLogger.info("AI", "AI_REQUEST_STARTED", "AI request started", TraceMeta.of(
                "reason", trigger,
                "provider", aiProvider.id(),
                "screenshotIncluded", evidence.screenshotIncluded(),
                "domIncluded", true,
                "cdpIncluded", evidence.cdpStatus() != null && !"unavailable".equals(evidence.cdpStatus()),
                "axIncluded", evidence.cdpStatus() != null && !"unavailable".equals(evidence.cdpStatus()),
                "graphIncluded", true,
                "evidenceSize", evidence.evidenceSize(),
                "mediaParts", media.size(),
                "url", evidence.url()
        ));
        try {
            TargetHypothesis primary;
            if (aiProvider instanceof FallbackAiProvider fallback) {
                int perCall = AiCalls.multimodalTimeoutSeconds(properties);
                int outer = Math.min(180, perCall * 3);
                FallbackAiProvider.DualOpinion<String> dual = fallback.generateStructuredDual(prompt, String.class)
                        .block(Duration.ofSeconds(Math.max(perCall + 10, outer)));
                String raw = dual == null ? null : dual.primary();
                primary = parseHypothesis(raw);
                if (dual != null && dual.hasSecondOpinion()) {
                    TargetHypothesis secondary = parseHypothesis(dual.secondary());
                    primary = mergeOpinions(primary, secondary, dual.secondaryProvider());
                }
            } else {
                String raw = AiCalls.awaitText(aiProvider, prompt, AiCalls.multimodalTimeoutSeconds(properties));
                primary = parseHypothesis(raw);
            }
            AiTelemetry.callCompleted(
                    trigger, aiProvider.id(), "", evidence.evidenceSize(), evidence.screenshotIncluded(), true,
                    System.currentTimeMillis() - started,
                    primary == null ? "UNKNOWN" : primary.classification(),
                    primary == null ? 0 : primary.confidence(),
                    primary == null ? "" : primary.recommendedStrategy(),
                    true,
                    "hypothesis_ready");
            TraceLogger.info("AI", "AI_RESPONSE_RECEIVED", "AI response received", TraceMeta.of(
                    "reason", trigger,
                    "provider", aiProvider.id(),
                    "latencyMs", System.currentTimeMillis() - started,
                    "classification", primary == null ? "UNKNOWN" : primary.classification(),
                    "confidence", primary == null ? 0 : primary.confidence(),
                    "visualTargetPresent", primary != null && primary.visualTargetPresent(),
                    "recommendedCandidateId", primary == null ? "" : primary.recommendedCandidateId(),
                    "screenshotIncluded", evidence.screenshotIncluded(),
                    "evidenceSize", evidence.evidenceSize()
            ));
            return primary == null ? TargetHypothesis.absent("AI returned no hypothesis") : primary;
        } catch (RuntimeException ex) {
            AiTelemetry.callCompleted(
                    trigger, aiProvider.id(), "", evidence.evidenceSize(), evidence.screenshotIncluded(), true,
                    System.currentTimeMillis() - started,
                    "AI_UNAVAILABLE", 0, "", false, "ai_unavailable");
            TraceLogger.warn("AI", "MULTIMODAL_AI_UNAVAILABLE", "Multimodal AI unavailable", TraceMeta.of(
                    "reason", trigger,
                    "provider", aiProvider.id(),
                    "screenshotIncluded", evidence.screenshotIncluded(),
                    "evidenceSize", evidence.evidenceSize(),
                    "latencyMs", System.currentTimeMillis() - started,
                    "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            ));
            return TargetHypothesis.aiUnavailable("AI unavailable");
        }
    }

    private TargetHypothesis mergeOpinions(TargetHypothesis primary, TargetHypothesis secondary, String secondaryProvider) {
        if (primary == null) {
            return secondary;
        }
        if (secondary == null) {
            return primary;
        }
        boolean agree = sameFamily(primary, secondary);
        if (agree) {
            return new TargetHypothesis(
                    primary.classification(),
                    primary.semanticField(),
                    primary.targetValue(),
                    primary.recommendedCandidateId(),
                    primary.recommendedStrategy(),
                    Math.min(0.99, Math.max(primary.confidence(), secondary.confidence()) + 0.05),
                    primary.visualTargetPresent() || secondary.visualTargetPresent(),
                    primary.domResolved() || secondary.domResolved(),
                    primary.reason(),
                    primary.candidateEvidence()
            );
        }
        TraceLogger.warn("AI", "AI_CONSENSUS_DISAGREE", "Gemini and Ollama disagreed; live re-inspect required", TraceMeta.of(
                "primary", primary.classification(),
                "secondary", secondary.classification(),
                "secondaryProvider", secondaryProvider == null ? "" : secondaryProvider
        ));
        return new TargetHypothesis(
                primary.classification(),
                primary.semanticField(),
                primary.targetValue(),
                primary.recommendedCandidateId(),
                primary.recommendedStrategy(),
                Math.min(primary.confidence(), 0.6),
                primary.visualTargetPresent(),
                false,
                "Providers disagreed; Safety Gate must re-verify live DOM. " + primary.reason(),
                primary.candidateEvidence()
        );
    }

    private TargetHypothesis parseHypothesis(String raw) {
        if (raw == null || raw.isBlank()) {
            return TargetHypothesis.absent("empty model output");
        }
        try {
            JsonNode node = objectMapper.readTree(JsonSupport.extractJson(raw));
            return TargetHypothesis.fromJson(node);
        } catch (RuntimeException ex) {
            return TargetHypothesis.absent("unparseable AI JSON");
        }
    }

    private static boolean sameFamily(TargetHypothesis a, TargetHypothesis b) {
        if (a.recommendedCandidateId() != null
                && a.recommendedCandidateId().equalsIgnoreCase(nullToEmpty(b.recommendedCandidateId()))) {
            return true;
        }
        return a.classification() != null && a.classification().equalsIgnoreCase(b.classification());
    }

    private String retrieveRag(SemanticTargetNormalizer.NormalizedTarget intent, String trigger) {
        RagRetrievalService rag = ragProvider == null ? null : ragProvider.getIfAvailable();
        if (rag == null) {
            return "";
        }
        try {
            RagRetrievalResult result = rag.retrieve(RagRetrievalRequest.builder()
                    .query(intent.ownedHint() + " " + trigger)
                    .failureCategory(intent.isFilterOption() ? "FILTER_TARGET" : "TARGET_DISCOVERY")
                    .contentTypeHint("PARENT_CHILD")
                    .topK(3)
                    .build()).block(Duration.ofSeconds(8));
            return result == null ? "" : result.toAdvisoryPromptBlock();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String formatCandidates(List<LocatorRanker.RankedElement> ranked) {
        if (ranked == null || ranked.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(8, ranked.size());
        for (int i = 0; i < n; i++) {
            LocatorRanker.RankedElement item = ranked.get(i);
            ElementCandidate el = item.element();
            sb.append("candidate-").append((char) ('A' + i))
                    .append(" id=").append(el.candidateId())
                    .append(" score=").append(Math.round(item.score()))
                    .append(" tag=").append(el.tag())
                    .append(" role=").append(el.role())
                    .append(" name=").append(el.accessibleName())
                    .append(" text=").append(el.text())
                    .append(" heading=").append(el.headingContext())
                    .append(" ancestors=").append(el.ancestorContext())
                    .append(" region=").append(el.region())
                    .append('\n');
        }
        return sb.toString();
    }

    private static byte[] captureScreenshot(Page page) {
        if (page == null) {
            return new byte[0];
        }
        try {
            return page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
        } catch (RuntimeException ex) {
            return new byte[0];
        }
    }

    private static int[] viewportOf(Page page) {
        if (page == null) {
            return new int[] {1280, 720};
        }
        try {
            var size = page.viewportSize();
            if (size == null) {
                return new int[] {1280, 720};
            }
            return new int[] {size.width, size.height};
        } catch (RuntimeException ex) {
            return new int[] {1280, 720};
        }
    }

    private static String summarizeFrames(List<ElementCandidate> elements) {
        List<String> frames = new ArrayList<>();
        for (ElementCandidate el : elements) {
            if (el.iframeContext() != null && !el.iframeContext().isBlank() && !frames.contains(el.iframeContext())) {
                frames.add(el.iframeContext());
            }
        }
        return frames.isEmpty() ? "main" : String.join(",", frames);
    }

    private static String summarizeShadow(List<ElementCandidate> elements) {
        long n = elements.stream()
                .filter(el -> el.shadowContext() != null && !el.shadowContext().isBlank())
                .count();
        return n == 0 ? "none" : n + " shadow nodes";
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
