package com.smartqa.browser;

import com.smartqa.ai.AiHealthStatus;
import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.AiProvider;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.DomExtractor;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceId;
import com.smartqa.event.ProgressEvent;
import com.smartqa.execution.event.ExecutionEventStore;
import com.smartqa.execution.screenshot.ScreenshotService;
import com.smartqa.generation.DeterministicPlaywrightFactory;
import com.smartqa.generation.QualityGateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SMARTQA_URBANCOMPANY_REAL", matches = "true")
class UrbanCompanyRuntimeRealRegressionTest {

    @Test
    void executesUrbanCompanyProfileLoginFlowInRealBrowser() throws Exception {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getBrowser().setType("chromium");
        properties.getBrowser().setHeadless(false);
        properties.getBrowser().setMaximizeHeaded(true);
        properties.getBrowser().setZoomPercent(50);
        properties.getExecution().setScreenshotMode("IMPORTANT");
        Path outDir = Path.of("D:/Smart_QA/.ui-e2e/evidence-urbancompany-direct");
        Files.createDirectories(outDir);
        properties.getScreenshots().setBaseDir(outDir.resolve("screenshots").toString());

        BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
        ElementResolver resolver = new ElementResolver(new NoopAiProvider(), JsonMapper.builder().build(), intelligence);
        PlaywrightBrowserExecutionProvider provider = new PlaywrightBrowserExecutionProvider(
                resolver,
                intelligence,
                properties,
                new ScreenshotService(properties),
                new ExecutionEventStore()
        );

        UUID testCaseId = UUID.randomUUID();
        String traceId = TraceId.newId();
        ExecutionPlan plan = new ExecutionPlan(
                testCaseId,
                "Urban Company profile login real-browser regression",
                "https://www.urbancompany.com/pune",
                List.of(
                        new ExecutionPlan.PlannedStep("1", "navigate", "https://www.urbancompany.com/pune", null, null, null, "AUTO"),
                        new ExecutionPlan.PlannedStep("2", "click", "profile icon", null, null, null, "TOP_RIGHT"),
                        new ExecutionPlan.PlannedStep("3", "click", "Login", null, null, null, "AUTO"),
                        new ExecutionPlan.PlannedStep("4", "verify", "Enter your phone number", null, "contains", null, "CENTER")
                )
        );

        List<ProgressEvent> events = new ArrayList<>();
        LocatorMemoryDocument memory = TraceContext.callChecked(traceId, () ->
                provider.execute(plan, events::add, null, new BrowserExecutionOptions("playwright", false)));

        boolean verified = memory.entries().stream()
                .anyMatch(entry -> "4".equals(entry.stepId())
                        && entry.semanticTarget() != null
                        && entry.semanticTarget().toLowerCase().contains("enter your phone number"));
        assertTrue(verified, "Expected verify step for 'Enter your phone number' to pass in real browser");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("result", "PASS");
        evidence.put("traceId", traceId);
        evidence.put("testCaseId", testCaseId.toString());
        evidence.put("finishedAt", Instant.now().toString());
        evidence.put("entries", memory.entries().stream().map(entry -> Map.of(
                "stepId", entry.stepId() == null ? "" : entry.stepId(),
                "action", entry.action() == null ? "" : entry.action(),
                "target", entry.semanticTarget() == null ? "" : entry.semanticTarget(),
                "locator", entry.resolvedLocator() == null ? "" : entry.resolvedLocator(),
                "locatorType", entry.locatorType() == null ? "" : entry.locatorType(),
                "controlType", entry.controlType() == null ? "" : entry.controlType(),
                "url", entry.pageUrl() == null ? "" : entry.pageUrl(),
                "text", entry.elementText() == null ? "" : entry.elementText()
        )).toList());
        evidence.put("progressTypes", events.stream().map(ProgressEvent::type).toList());
        Path screenshotRoot = outDir.resolve("screenshots").resolve(traceId);
        List<String> screenshots = List.of();
        if (Files.isDirectory(screenshotRoot)) {
            try (Stream<Path> files = Files.list(screenshotRoot)) {
                screenshots = files
                        .filter(path -> path.toString().endsWith(".png"))
                        .map(Path::toString)
                        .toList();
            }
        }
        evidence.put("screenshots", screenshots);
        Path evidenceFile = outDir.resolve("urbancompany-runtime-evidence.json");
        Files.writeString(evidenceFile, JsonMapper.builder().build().writeValueAsString(evidence));

        String generated = DeterministicPlaywrightFactory.render("UrbanCompanyRecordedFlow", memory);
        Path generatedFile = outDir.resolve("generated-playwright.java");
        Files.writeString(generatedFile, generated);
        QualityGateService.QualityGateResult qualityGate = new QualityGateService().validateAndCompile(generated);
        evidence.put("qualityGatePassed", qualityGate.passed());
        evidence.put("qualityGateMessage", qualityGate.message());
        Files.writeString(evidenceFile, JsonMapper.builder().build().writeValueAsString(evidence));
        assertTrue(qualityGate.passed(), "Quality gate failed for generated Playwright: " + qualityGate.message());

        Path marker = outDir.resolve("urbancompany-runtime-pass-" + Instant.now().toEpochMilli() + ".txt");
        Files.writeString(marker, "PASS testCaseId=" + testCaseId + " traceId=" + traceId
                + " entries=" + memory.entries().size() + " screenshots=" + screenshots.size());
    }

    private static final class NoopAiProvider implements AiProvider {
        @Override
        public String id() {
            return "noop";
        }

        @Override
        public Mono<String> generateText(AiPrompt prompt) {
            return Mono.just("{\"locatorType\":\"css\",\"resolvedLocator\":\"\"}");
        }

        @Override
        public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
            return Mono.error(new UnsupportedOperationException("No structured output"));
        }

        @Override
        public Mono<AiHealthStatus> healthCheck() {
            return Mono.just(AiHealthStatus.available("noop", null, null, 0));
        }
    }
}
