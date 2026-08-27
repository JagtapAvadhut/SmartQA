package com.smartqa.generation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.smartqa.browser.BrowserExecutionProvider;
import com.smartqa.browser.ExecutionPlan;
import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.common.config.BlockingWorkConfig;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.execution.cancel.CancellationToken;
import com.smartqa.execution.cancel.ExecutionCancellationRegistry;
import com.smartqa.intent.IntentContract;
import com.smartqa.intent.IntentScenario;
import com.smartqa.intent.IntentService;
import com.smartqa.intent.IntentStep;
import com.smartqa.project.Project;
import com.smartqa.project.ProjectRepository;
import com.smartqa.testcase.TestCase;
import com.smartqa.testcase.TestCaseResponse;
import com.smartqa.testcase.TestCaseService;
import com.smartqa.testcase.TestCaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final TestCaseService testCaseService;
    private final ProjectRepository projectRepository;
    private final IntentService intentService;
    private final BrowserExecutionProvider browserExecutionProvider;
    private final PlaywrightJavaCodeGenerator codeGenerator;
    private final QualityGateService qualityGateService;
    private final ProgressEventHub eventHub;
    private final JsonMapper objectMapper;
    private final Scheduler blockingScheduler;
    private final ExecutionCancellationRegistry cancellationRegistry;

    public GenerationService(
            TestCaseService testCaseService,
            ProjectRepository projectRepository,
            IntentService intentService,
            BrowserExecutionProvider browserExecutionProvider,
            PlaywrightJavaCodeGenerator codeGenerator,
            QualityGateService qualityGateService,
            ProgressEventHub eventHub,
            JsonMapper objectMapper,
            @Qualifier(BlockingWorkConfig.BLOCKING_SCHEDULER) Scheduler blockingScheduler,
            ExecutionCancellationRegistry cancellationRegistry) {
        this.testCaseService = testCaseService;
        this.projectRepository = projectRepository;
        this.intentService = intentService;
        this.browserExecutionProvider = browserExecutionProvider;
        this.codeGenerator = codeGenerator;
        this.qualityGateService = qualityGateService;
        this.eventHub = eventHub;
        this.objectMapper = objectMapper;
        this.blockingScheduler = blockingScheduler;
        this.cancellationRegistry = cancellationRegistry;
    }

    public Mono<TestCaseResponse> generateAsync(UUID testCaseId, UUID generationRunId) {
        return generate(testCaseId, generationRunId);
    }

    public Mono<TestCaseResponse> generate(UUID testCaseId) {
        return generate(testCaseId, null);
    }

    public Mono<TestCaseResponse> generate(UUID testCaseId, UUID generationRunId) {
        UUID pipelineId = com.smartqa.event.RunCorrelation.pipelineRunId();
        UUID genId = generationRunId;
        com.smartqa.event.RunCorrelation.set(pipelineId, genId, testCaseId);
        String channel = ProgressEventHub.generationChannel(testCaseId);
        long started = System.nanoTime();
        return testCaseService.requireEntity(testCaseId)
                .flatMap(testCase -> projectRepository.findById(testCase.getProjectId())
                        .switchIfEmpty(Mono.error(new SmartQaException(ErrorCode.RESOURCE_NOT_FOUND, "Project not found")))
                        .flatMap(project -> ensureIntent(testCase)
                                .flatMap(intent -> testCaseService.requireEntity(testCase.getId())
                                        .map(fresh -> new Ctx(fresh, project, intent)))))
                .doOnSubscribe(ignored -> {
                    log.info("test_generation_started testCaseId={}", testCaseId);
                    TraceLogger.info("SERVICE", "ENTER", "GENERATE_TEST", TraceMeta.of("testCaseId", testCaseId.toString()));
                    emit(channel, testCaseId, generationRunId, "GENERATION_STARTED", "Starting generation");
                    com.smartqa.browser.BrowserLifecycle.info(com.smartqa.browser.BrowserLifecycle.GENERATION_START, "Generation started");
                })
                .flatMap(ctx -> {
                    TraceLogger.info("SERVICE", "EXECUTION_PLAN", "Execution plan prepared", TraceMeta.of(
                            "applicationUrl", ctx.project().getApplicationUrl(),
                            "steps", toPlan(ctx.testCase(), ctx.project(), ctx.intent()).steps().size()
                    ));
                    return runBrowser(ctx, channel)
                            .flatMap(memory -> persistMemory(ctx.testCase(), memory).thenReturn(new Ctx(ctx.testCase(), ctx.project(), ctx.intent(), memory)));
                })
                .flatMap(ctx -> {
                    emit(channel, testCaseId, generationRunId, "CODE_GENERATING", "Generating Playwright code");
                    int steps = ctx.memory() == null || ctx.memory().entries() == null ? 0 : ctx.memory().entries().size();
                    TraceLogger.info("CODEGEN", "CODE_GENERATION_STARTED", "Generating Playwright code", TraceMeta.of(
                            "steps", steps,
                            "locators", steps
                    ));
                    String className = className(ctx.testCase().getName());
                    String deterministic = prepareGeneratedCode(
                            DeterministicPlaywrightFactory.render(className, ctx.memory()), ctx.memory());
                    return Mono.fromCallable(() -> qualityGateService.validateAndCompile(deterministic))
                            .subscribeOn(blockingScheduler)
                            .flatMap(gate -> {
                                if (gate.passed()) {
                                    TraceLogger.info("CODEGEN", "DETERMINISTIC_CODE_USED",
                                            "Skipped Gemini codegen; deterministic code passed quality gate",
                                            TraceMeta.of(
                                                    "className", className,
                                                    "codeLength", deterministic.length()
                                            ));
                                    return Mono.just(new Ctx(ctx.testCase(), ctx.project(), ctx.intent(), ctx.memory(), deterministic, className));
                                }
                                TraceLogger.warn("CODEGEN", "DETERMINISTIC_CODE_REJECTED", gate.message(), TraceMeta.of(
                                        "fallback", "gemini"
                                ));
                                return codeGenerator.generate(ctx.testCase(), ctx.intent(), ctx.memory(), className)
                                        .timeout(Duration.ofSeconds(45))
                                        .onErrorReturn(deterministic)
                                        .defaultIfEmpty(deterministic)
                                        .map(code -> new Ctx(ctx.testCase(), ctx.project(), ctx.intent(), ctx.memory(), code, className));
                            });
                })
                .flatMap(ctx -> Mono.fromCallable(() -> TraceContext.call(TraceContext.current(), () -> {
                            var gate = qualityGateService.validateAndCompile(ctx.code());
                            String code = ctx.code();
                            if (!gate.passed()) {
                                log.warn("quality_gate_ai_failed testCaseId={} reason={}", testCaseId, gate.message());
                                TraceLogger.warn("QUALITY_GATE", "QUALITY_GATE_FAILED", gate.message(), TraceMeta.of(
                                        "stage", "AI_CODE",
                                        "fallback", "deterministic"
                                ));
                                code = prepareGeneratedCode(
                                        DeterministicPlaywrightFactory.render(ctx.className(), ctx.memory()),
                                        ctx.memory());
                                gate = qualityGateService.requirePass(code);
                            } else {
                                qualityGateService.requirePass(code);
                            }
                            emit(channel, testCaseId, generationRunId, "CODE_GENERATED", "Code generated");
                            emit(channel, testCaseId, generationRunId, "QUALITY_GATE_PASSED", gate.message());
                            log.info("quality_gate_result testCaseId={} passed=true", testCaseId);
                            TraceLogger.info("CODEGEN", "CODE_GENERATION_COMPLETED", "Code generated", TraceMeta.of(
                                    "codeLength", code.length()
                            ));
                            ctx.testCase().setGeneratedCode(code);
                            ctx.testCase().setStatus(TestCaseStatus.READY);
                            return ctx.testCase();
                        })).subscribeOn(blockingScheduler)
                        .flatMap(testCaseService::saveEntity))
                .flatMap(saved -> {
                    emit(channel, testCaseId, generationRunId, "GENERATION_COMPLETE", "Generation complete");
                    TraceLogger.info("SERVICE", "EXIT", "GENERATE_TEST",
                            (System.nanoTime() - started) / 1_000_000,
                            TraceMeta.of("status", "SUCCESS", "testCaseId", testCaseId.toString()));
                    TraceLogger.info("HTTP", "TRACE_END", "Generation finished", TraceMeta.of("status", "SUCCESS"));
                    return testCaseService.get(testCaseId);
                })
                .doOnError(error -> {
                    log.error("generation_error testCaseId={}", testCaseId, error);
                    TraceLogger.error("SERVICE", "GENERATE_TEST", "Generation failed", error,
                            (System.nanoTime() - started) / 1_000_000,
                            TraceMeta.of("testCaseId", testCaseId.toString()));
                    TraceLogger.info("HTTP", "TRACE_END", "Generation finished", TraceMeta.of("status", "FAILED"));
                    emit(channel, testCaseId, generationRunId, "GENERATION_ERROR", safeMessage(error));
                })
                .doOnEach(signal -> com.smartqa.event.RunCorrelation.set(pipelineId, genId, testCaseId))
                .contextWrite(ctx -> com.smartqa.event.RunCorrelation.writeContext(ctx, pipelineId, genId, testCaseId));
    }

    public Mono<TestCaseResponse> saveCode(UUID testCaseId, GeneratedCodeRequest request) {
        if (request == null || request.generatedCode() == null || request.generatedCode().isBlank()) {
            return Mono.error(new SmartQaException(ErrorCode.VALIDATION_FAILED, "Generated code is required"));
        }
        return testCaseService.requireEntity(testCaseId)
                .flatMap(testCase -> Mono.fromCallable(() -> {
                    qualityGateService.requirePass(request.generatedCode());
                    testCase.setGeneratedCode(request.generatedCode());
                    testCase.setStatus(TestCaseStatus.READY);
                    return testCase;
                }).subscribeOn(blockingScheduler))
                .flatMap(testCaseService::saveEntity)
                .flatMap(saved -> testCaseService.get(testCaseId));
    }

    private Mono<IntentContract> ensureIntent(TestCase testCase) {
        IntentContract existing = intentService.readContract(testCase.getIntentContract());
        if (existing == null) {
            return intentService.understand(testCase.getId())
                    .map(response -> intentService.readContract(response.intentContract()));
        }
        if (IntentContract.NEEDS_CLARIFICATION.equals(existing.status())) {
            return Mono.error(new SmartQaException(
                    ErrorCode.CLARIFICATION_REQUIRED,
                    "Resolve clarifications before generating"));
        }
        return Mono.just(existing);
    }

    private Mono<LocatorMemoryDocument> runBrowser(Ctx ctx, String channel) {
        ExecutionPlan plan = toPlan(ctx.testCase(), ctx.project(), ctx.intent());
        emit(channel, ctx.testCase().getId(), com.smartqa.event.RunCorrelation.generationRunId(), "BROWSER_STARTED", "Building execution plan");
        TraceLogger.info("SERVICE", "ENTER", "BROWSER_EXECUTION", TraceMeta.of("steps", plan.steps().size()));
        String traceId = TraceContext.current();
        CancellationToken token = cancellationRegistry.register(ctx.testCase().getId());
        return Mono.fromCallable(() -> TraceContext.call(traceId, () -> browserExecutionProvider.execute(plan, event -> eventHub.emit(channel, event), token)))
                .subscribeOn(blockingScheduler)
                .doOnCancel(() -> com.smartqa.browser.BrowserLifecycle.warn("SUBSCRIBER_CANCELLED",
                        "Generation subscriber cancelled; browser is not closed by SSE disconnect",
                        com.smartqa.debug.TraceMeta.of("testCaseId", ctx.testCase().getId().toString())))
                .doFinally(signal -> {
                    cancellationRegistry.unregister(ctx.testCase().getId());
                    com.smartqa.browser.BrowserLifecycle.info(com.smartqa.browser.BrowserLifecycle.GENERATION_END, "Generation browser phase ended");
                });
    }

    private Mono<TestCase> persistMemory(TestCase testCase, LocatorMemoryDocument memory) {
        try {
            testCase.setLocatorMemory(objectMapper.writeValueAsString(memory));
        } catch (JacksonException ex) {
            return Mono.error(new SmartQaException(ErrorCode.INTERNAL_ERROR, "Unable to store locator memory", ex));
        }
        return testCaseService.saveEntity(testCase);
    }

    static ExecutionPlan toPlan(TestCase testCase, Project project, IntentContract intent) {
        List<ExecutionPlan.PlannedStep> steps = new ArrayList<>();
        if (intent.scenarios() != null) {
            for (IntentScenario scenario : intent.scenarios()) {
                if (scenario.steps() == null) {
                    continue;
                }
                for (IntentStep step : com.smartqa.intent.IntentPlanDag.inferSequentialDependsOn(scenario.steps())) {
                    steps.add(ExecutionPlan.PlannedStep.from(step));
                }
            }
        }
        return new ExecutionPlan(testCase.getId(), testCase.getName(), project.getApplicationUrl(), steps);
    }

    static String prepareGeneratedCode(String source, LocatorMemoryDocument memory) {
        String sanitized = GeneratedCodeSanitizer.sanitize(source);
        return GeneratedCodeNavigationContract.stripUnrecordedNavigations(sanitized, memory);
    }

    static String className(String testName) {
        StringBuilder builder = new StringBuilder();
        boolean upper = true;
        for (char ch : (testName == null ? "GeneratedTest" : testName).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                builder.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            } else {
                upper = true;
            }
        }
        if (builder.isEmpty()) {
            builder.append("GeneratedTest");
        }
        if (Character.isDigit(builder.charAt(0))) {
            builder.insert(0, "Test");
        }
        if (!builder.toString().endsWith("Test")) {
            builder.append("Test");
        }
        return builder.toString();
    }

    private void emit(String channel, UUID testCaseId, String type, String message) {
        emit(channel, testCaseId, com.smartqa.event.RunCorrelation.generationRunId(), type, message);
    }

    private void emit(String channel, UUID testCaseId, UUID generationRunId, String type, String message) {
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        if (generationRunId != null) {
            details.put("generationRunId", generationRunId.toString());
        }
        if (com.smartqa.event.RunCorrelation.pipelineRunId() != null) {
            details.put("pipelineRunId", com.smartqa.event.RunCorrelation.pipelineRunId().toString());
        }
        if (testCaseId != null) {
            details.put("testCaseId", testCaseId.toString());
        }
        eventHub.emit(channel, ProgressEvent.generation(type, message, testCaseId, details));
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? "Generation failed" : error.getMessage();
    }

    private record Ctx(
            TestCase testCase,
            Project project,
            IntentContract intent,
            LocatorMemoryDocument memory,
            String code,
            String className
    ) {
        private Ctx(TestCase testCase, Project project, IntentContract intent) {
            this(testCase, project, intent, null, null, null);
        }

        private Ctx(TestCase testCase, Project project, IntentContract intent, LocatorMemoryDocument memory) {
            this(testCase, project, intent, memory, null, null);
        }
    }
}
