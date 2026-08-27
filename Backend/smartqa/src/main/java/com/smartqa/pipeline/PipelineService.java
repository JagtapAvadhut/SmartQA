package com.smartqa.pipeline;

import com.smartqa.ai.FallbackAiProvider;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceId;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.event.RunCorrelation;
import com.smartqa.execution.ExecutionRunResponse;
import com.smartqa.execution.ExecutionService;
import com.smartqa.execution.ExecutionStartRequest;
import com.smartqa.execution.cancel.ExecutionCancellationRegistry;
import com.smartqa.generation.GenerationRun;
import com.smartqa.generation.GenerationRunRegistry;
import com.smartqa.generation.GenerationService;
import com.smartqa.intent.ClarificationQuestion;
import com.smartqa.intent.IntentContract;
import com.smartqa.intent.IntentService;
import com.smartqa.project.ProjectRepository;
import com.smartqa.rag.EmbeddingProvider;
import com.smartqa.testcase.TestCaseResponse;
import com.smartqa.testcase.TestCaseService;
import com.smartqa.validation.GeneratedTestValidator;
import com.smartqa.validation.ValidationRunResponse;
import com.smartqa.workspace.WorkspaceAnalyzeRequest;
import com.smartqa.workspace.WorkspaceAnalyzeResponse;
import com.smartqa.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-click coordinated QA team orchestration (URL + Instructions → Generate & Validate).
 * <p>
 * Per step: live evidence → candidates → score → high-confidence deterministic execute,
 * or AI (compact evidence) → Deterministic Safety Gate → Playwright → fresh state → assert.
 * On fail: FailureEvidence → AI Diagnosis → Safe Recovery → live verify → retry.
 * On cross-site generic defect: GENERIC_ENGINE_DEFECT → SourceFixProposal → Cursor fix loop.
 * See docs/SMARTQA_TEAM_ORCHESTRATION.md.
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Pattern EXPECTED_TEXT = Pattern.compile(
            "expected text\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final WorkspaceService workspaceService;
    private final IntentService intentService;
    private final GenerationService generationService;
    private final GenerationRunRegistry generationRunRegistry;
    private final GeneratedTestValidator validator;
    private final ExecutionService executionService;
    private final TestCaseService testCaseService;
    private final ProjectRepository projectRepository;
    private final FallbackAiProvider aiProvider;
    private final FailureDiagnostician diagnostician;
    private final FailureAwareRecoveryService failureAwareRecoveryService;
    private final DevelopmentFixLoopService developmentFixLoopService;
    private final PipelineRunRegistry pipelineRunRegistry;
    private final ProgressEventHub eventHub;
    private final SmartQaProperties properties;
    private final ExecutionCancellationRegistry cancellationRegistry;
    private final PipelineRunPersistenceService pipelineRunPersistence;
    private final EmbeddingProvider embeddingProvider;

    public PipelineService(
            WorkspaceService workspaceService,
            IntentService intentService,
            GenerationService generationService,
            GenerationRunRegistry generationRunRegistry,
            GeneratedTestValidator validator,
            ExecutionService executionService,
            TestCaseService testCaseService,
            ProjectRepository projectRepository,
            FallbackAiProvider aiProvider,
            FailureDiagnostician diagnostician,
            FailureAwareRecoveryService failureAwareRecoveryService,
            DevelopmentFixLoopService developmentFixLoopService,
            PipelineRunRegistry pipelineRunRegistry,
            ProgressEventHub eventHub,
            SmartQaProperties properties,
            ExecutionCancellationRegistry cancellationRegistry,
            PipelineRunPersistenceService pipelineRunPersistence,
            EmbeddingProvider embeddingProvider) {
        this.workspaceService = workspaceService;
        this.intentService = intentService;
        this.generationService = generationService;
        this.generationRunRegistry = generationRunRegistry;
        this.validator = validator;
        this.executionService = executionService;
        this.testCaseService = testCaseService;
        this.projectRepository = projectRepository;
        this.aiProvider = aiProvider;
        this.diagnostician = diagnostician;
        this.failureAwareRecoveryService = failureAwareRecoveryService;
        this.developmentFixLoopService = developmentFixLoopService;
        this.pipelineRunRegistry = pipelineRunRegistry;
        this.eventHub = eventHub;
        this.properties = properties;
        this.cancellationRegistry = cancellationRegistry;
        this.pipelineRunPersistence = pipelineRunPersistence;
        this.embeddingProvider = embeddingProvider;
    }

    public Mono<PipelineRunResponse> start(PipelineStartRequest request) {
        PipelineRun run = PipelineRun.start();
        int maxAttempts = request.maxAttempts() == null || request.maxAttempts() < 1
                ? DEFAULT_MAX_ATTEMPTS
                : Math.min(request.maxAttempts(), 5);
        run.setMaxAttempts(maxAttempts);
        pipelineRunRegistry.register(run);

        String incomingTrace = TraceContext.resolveOrCreate();
        run.putDetail("traceId", incomingTrace);
        final String boundTrace = incomingTrace;

        TraceLogger.info("PIPELINE", "PIPELINE_STARTED", "Generate & Validate pipeline started", TraceMeta.of(
                "pipelineId", run.id().toString(),
                "maxAttempts", maxAttempts,
                "traceId", boundTrace
        ));

        run.advance(PipelineStage.PREFLIGHT, "Checking services");
        emit(run, "PIPELINE_STARTED", "Starting autonomous test pipeline", Map.of(
                "userStage", "Checking services"
        ));

        preflight(run, request)
                .then(Mono.defer(() -> runPipeline(run, request)))
                .contextWrite(ctx -> RunCorrelation.writeContext(
                        TraceContext.writeContext(ctx, boundTrace),
                        run.id(), run.generationRunId(), run.testCaseId()))
                .doOnEach(signal -> bindTrace(run, boundTrace, signal.getContextView()))
                .doOnError(error -> log.error("pipeline_failed pipelineId={}", run.id(), error))
                .doFinally(signal -> pipelineRunPersistence.persistAsync(run))
                .subscribe(
                        ignored -> log.info("pipeline_finished pipelineId={} status={}", run.id(), run.status()),
                        error -> {
                            if (!run.isTerminal()) {
                                diagnoseAndFinish(run, run.stage() == null ? "PIPELINE" : run.stage().name(), safe(error))
                                        .doFinally(sig -> pipelineRunPersistence.persistAsync(run))
                                        .subscribe();
                            }
                        }
                );

        return Mono.just(PipelineRunResponse.from(run));
    }

    public Mono<PipelineRunResponse> getLatestByTestCaseId(UUID testCaseId) {
        PipelineRun live = pipelineRunRegistry.getByTestCase(testCaseId);
        if (live != null) {
            return enrich(live);
        }
        return pipelineRunPersistence.findLatestByTestCaseId(testCaseId)
                .flatMap(persisted -> {
                    pipelineRunRegistry.register(persisted);
                    return enrich(persisted);
                });
    }

    public Mono<PipelineRunResponse> get(UUID pipelineId) {
        PipelineRun run = pipelineRunRegistry.get(pipelineId);
        if (run != null) {
            return enrich(run);
        }
        return pipelineRunPersistence.findById(pipelineId)
                .flatMap(persisted -> {
                    pipelineRunRegistry.register(persisted);
                    return enrich(persisted);
                })
                .switchIfEmpty(Mono.error(new SmartQaException(ErrorCode.RESOURCE_NOT_FOUND, "Pipeline run not found")));
    }

    public Mono<PipelineRunResponse> stop(UUID pipelineId) {
        PipelineRun run = pipelineRunRegistry.get(pipelineId);
        if (run == null) {
            return Mono.error(new SmartQaException(ErrorCode.RESOURCE_NOT_FOUND, "Pipeline run not found"));
        }
        if (run.isTerminal()) {
            return Mono.just(PipelineRunResponse.from(run));
        }
        run.requestStop();
        // Generation registers cancellation by testCaseId; execution by executionRunId.
        if (run.testCaseId() != null) {
            cancellationRegistry.requestStop(run.testCaseId());
        }
        if (run.executionRunId() != null) {
            cancellationRegistry.requestStop(run.executionRunId());
            return executionService.stop(run.executionRunId())
                    .onErrorResume(ignored -> Mono.empty())
                    .then(Mono.fromCallable(() -> {
                        if (!run.isTerminal()) {
                            run.markStopped();
                            emit(run, "PIPELINE_STOPPED", "Pipeline stopped", Map.of("status", PipelineRun.STATUS_STOPPED));
                            pipelineRunPersistence.persistAsync(run);
                        }
                        return PipelineRunResponse.from(run);
                    }));
        }
        if (!run.isTerminal()) {
            run.markStopped();
            emit(run, "PIPELINE_STOPPED", "Pipeline stopped", Map.of("status", PipelineRun.STATUS_STOPPED));
            pipelineRunPersistence.persistAsync(run);
        }
        return Mono.just(PipelineRunResponse.from(run));
    }

    public Mono<SourceFixProposal> requestFixAndRebuild(UUID pipelineId) {
        PipelineRun run = pipelineRunRegistry.get(pipelineId);
        if (run == null) {
            return Mono.error(new SmartQaException(ErrorCode.RESOURCE_NOT_FOUND, "Pipeline run not found"));
        }
        SourceFixProposal proposal = run.sourceFix();
        if (proposal == null && run.diagnosis() != null) {
            proposal = run.diagnosis().sourceFix();
        }
        if (proposal == null) {
            return Mono.error(new SmartQaException(ErrorCode.RESOURCE_NOT_FOUND, "No source fix proposal for this pipeline"));
        }
        return developmentFixLoopService.requestFixAndRebuild(proposal.id())
                .doOnNext(updated -> {
                    run.setSourceFix(updated);
                    emit(run, "PIPELINE_SOURCE_FIX_REQUESTED", "Fix & Rebuild requested", Map.of(
                            "proposalId", updated.id(),
                            "status", updated.status()
                    ));
                });
    }

    static boolean skipAiPreflight(PipelineStartRequest request) {
        return request != null && request.structuredSteps() != null && !request.structuredSteps().isEmpty();
    }

    private Mono<Void> preflight(PipelineRun run, PipelineStartRequest request) {
        boolean skipAi = skipAiPreflight(request);
        return projectRepository.count()
                .timeout(Duration.ofSeconds(3))
                .onErrorMap(error -> new SmartQaException(
                        ErrorCode.VALIDATION_FAILED,
                        "Database is unavailable. Cannot start Generate & Validate.",
                        error))
                .then(skipAi
                        ? Mono.fromRunnable(() -> emit(run, "AI_PREFLIGHT_SKIPPED",
                        "Structured steps are ready — skipping AI health check",
                        Map.of("status", "SKIPPED_AI_PREFLIGHT")))
                        : aiProvider.healthAll()
                        .timeout(Duration.ofSeconds(12))
                        .flatMap(snapshot -> {
                            boolean anyUsable = false;
                            if (snapshot.providers() != null) {
                                for (var health : snapshot.providers()) {
                                    String id = health.provider() == null ? "" : health.provider().toLowerCase(Locale.ROOT);
                                    String reason = health.reason() == null ? "" : health.reason();
                                    if ("gemini".equals(id) && (reason.contains("429") || "GEMINI_RATE_LIMITED".equals(reason))) {
                                        emit(run, "GEMINI_RATE_LIMITED", "Gemini is rate limited", Map.of(
                                                "status", "GEMINI_RATE_LIMITED",
                                                "reason", reason
                                        ));
                                    }
                                    if ("ollama".equals(id) && !health.usable()) {
                                        emit(run, "OLLAMA_UNAVAILABLE", "Ollama is unavailable", Map.of(
                                                "status", "OLLAMA_UNAVAILABLE",
                                                "reason", reason
                                        ));
                                    }
                                    if (health.usable()) {
                                        anyUsable = true;
                                    }
                                }
                            }
                            if (!anyUsable) {
                                return Mono.error(new SmartQaException(
                                        ErrorCode.AI_UNAVAILABLE,
                                        "Mandatory AI is unavailable (GEMINI_RATE_LIMITED / OLLAMA_UNAVAILABLE)."));
                            }
                            return Mono.<Void>empty();
                        })
                        .onErrorResume(error -> {
                            if (error instanceof SmartQaException) {
                                return Mono.error(error);
                            }
                            return Mono.error(new SmartQaException(
                                    ErrorCode.AI_UNAVAILABLE,
                                    "AI health check failed",
                                    error));
                        }))
                .then(embeddingProvider.available()
                        .timeout(Duration.ofSeconds(8))
                        .flatMap(ok -> {
                            if (!Boolean.TRUE.equals(ok)) {
                                emit(run, "EMBEDDING_UNAVAILABLE", "Embedding health check failed", Map.of(
                                        "status", "EMBEDDING_UNAVAILABLE"
                                ));
                            }
                            return Mono.empty();
                        })
                        .onErrorResume(error -> {
                            emit(run, "EMBEDDING_UNAVAILABLE", "Embedding health check failed", Map.of(
                                    "status", "EMBEDDING_UNAVAILABLE"
                            ));
                            return Mono.empty();
                        }))
                .then();
    }

    private Mono<PipelineRunResponse> runPipeline(PipelineRun run, PipelineStartRequest request) {
        bindTrace(run, storedTrace(run), null);
        return analyze(run, request)
                .flatMap(analyzed -> {
                    throwIfStopped(run);
                    TestCaseResponse testCase = analyzed.testCase();
                    run.bindWorkspace(analyzed.project().id(), testCase.id(), request.applicationUrl());
                    pipelineRunRegistry.bindTestCase(run, testCase.id());

                    IntentContract contract = intentService.readContract(testCase.intentContract());
                    if (contract != null && IntentContract.NEEDS_CLARIFICATION.equals(contract.status())) {
                        List<ClarificationQuestion> questions = contract.clarifications() == null
                                ? List.of()
                                : contract.clarifications();
                        FailureDiagnosis diagnosis = FailureDiagnosis.of(
                                "Clarification required before autonomous execution",
                                "User instruction maps to multiple possible targets or incomplete steps.",
                                "Test Understanding Agent",
                                "USER_INSTRUCTION",
                                "status=NEEDS_CLARIFICATION; questions=" + questions.size(),
                                false,
                                run.attempt(),
                                "Answer the clarification questions, then click Generate & Validate Test again."
                        );
                        run.markBlocked("Clarification needed", diagnosis);
                        run.putDetail("clarificationCount", questions.size());
                        emit(run, "PIPELINE_BLOCKED", "Clarification needed", Map.of(
                                "status", PipelineRun.STATUS_BLOCKED,
                                "clarifications", questions.size()
                        ));
                        return Mono.just(PipelineRunResponse.from(run, testCase, questions));
                    }

                    emit(run, "PIPELINE_STAGE", "Test understood", Map.of("userStage", "Understanding test", "done", true));
                    return attemptLoop(run, request, testCase.id());
                });
    }

    private Mono<WorkspaceAnalyzeResponse> analyze(PipelineRun run, PipelineStartRequest request) {
        run.advance(PipelineStage.UNDERSTAND, "Understanding test");
        emit(run, "PIPELINE_STAGE", "Understanding test", Map.of("userStage", "Understanding test"));
        if (!skipAiPreflight(request)) {
            emit(run, "AI_WAIT", "Contacting AI to understand the test. This can take a minute.",
                    Map.of("userStage", "Understanding test"));
        }
        WorkspaceAnalyzeRequest analyzeRequest = new WorkspaceAnalyzeRequest(
                request.applicationUrl(),
                request.instructions(),
                request.projectId(),
                request.testCaseId(),
                request.structuredSteps()
        );
        return workspaceService.analyze(analyzeRequest)
                .doOnNext(ignored -> run.advance(PipelineStage.PLAN, "Understanding test"));
    }

    private Mono<PipelineRunResponse> attemptLoop(PipelineRun run, PipelineStartRequest request, UUID testCaseId) {
        return Mono.defer(() -> oneAttempt(run, request, testCaseId))
                .flatMap(outcome -> {
                    if ("PASS".equals(outcome) || "BLOCKED".equals(outcome) || "STOPPED".equals(outcome)) {
                        return enrich(run);
                    }
                    return diagnoseFailure(run, outcome)
                            .flatMap(enriched -> {
                                if (enriched.diagnosis().aiDiagnosis() != null
                                        && enriched.diagnosis().aiDiagnosis().requiresUserInput()) {
                                    run.markBlocked(
                                            enriched.diagnosis().aiDiagnosis().userQuestion() == null
                                                    ? "Clarification needed"
                                                    : enriched.diagnosis().aiDiagnosis().userQuestion(),
                                            enriched.diagnosis());
                                    emit(run, "PIPELINE_BLOCKED", "Clarification needed", Map.of(
                                            "status", PipelineRun.STATUS_BLOCKED,
                                            "userQuestion", enriched.diagnosis().aiDiagnosis().userQuestion() == null
                                                    ? "" : enriched.diagnosis().aiDiagnosis().userQuestion()
                                    ));
                                    return enrich(run);
                                }
                                run.setRecoveryHints(enriched.recoveryHints());
                                if (enriched.sourceFix() != null) {
                                    run.setSourceFix(enriched.sourceFix());
                                }
                                boolean retry = enriched.shouldRetry()
                                        && diagnostician.shouldAutoRetry(enriched.diagnosis(), run.attempt(), run.maxAttempts())
                                        && !run.isStopRequested();
                                // Prefer FailureAware decision when it says retry for WRONG_HOST etc.
                                if (!retry && enriched.shouldRetry() && run.attempt() < run.maxAttempts()
                                        && !run.isStopRequested()) {
                                    retry = true;
                                }
                                if (retry) {
                                    int next = run.attempt() + 1;
                                    run.setAttempt(next);
                                    run.reopenForRetry();
                                    run.advance(PipelineStage.RECOVER, "Recovering and retrying");
                                    emit(run, "PIPELINE_RETRY", "Automatic recovery attempt " + next,
                                            Map.of(
                                                    "attempt", next,
                                                    "category", enriched.diagnosis().category(),
                                                    "rootCause", enriched.diagnosis().rootCause() == null
                                                            ? "" : enriched.diagnosis().rootCause(),
                                                    "recoveryHints", enriched.recoveryHints()
                                            ));
                                    return attemptLoop(run, request, testCaseId);
                                }
                                run.markFail(
                                        run.errorMessage() == null ? "Pipeline failed" : run.errorMessage(),
                                        enriched.diagnosis());
                                emit(run, "PIPELINE_FAILED", run.errorMessage(), Map.of(
                                        "status", PipelineRun.STATUS_FAIL,
                                        "category", enriched.diagnosis().category(),
                                        "attempts", run.attempt(),
                                        "requiresSourceFix", enriched.diagnosis().requiresSourceFix(),
                                        "autoRecoveryAttempted", enriched.diagnosis().autoRecoveryAttempted()
                                ));
                                return enrich(run);
                            });
                });
    }

    private Mono<FailureAwareRecoveryService.EnrichedFailure> diagnoseFailure(PipelineRun run, String outcome) {
        run.advance(PipelineStage.DIAGNOSE, "Diagnosing failure");
        emit(run, "PIPELINE_STAGE", "Diagnosing failure", Map.of("userStage", "Diagnosing failure"));
        String message = run.errorMessage() == null ? "Pipeline failed" : run.errorMessage();
        String stage = run.stage() == null ? "PIPELINE" : run.stage().name();
        if ("FAIL".equals(outcome) && run.diagnosis() != null && run.diagnosis().category() != null) {
            // Keep stage from last soft-fail when available via details
            Object lastStage = run.details().get("lastFailedStage");
            if (lastStage != null) {
                stage = String.valueOf(lastStage);
            }
        }
        String expected = extractExpected(message);
        String actual = message;
        run.addAttemptHistory(stage + ":" + (run.diagnosis() == null ? "UNKNOWN" : run.diagnosis().category()));
        return failureAwareRecoveryService.enrich(
                run,
                stage,
                message,
                expected,
                actual,
                null,
                null,
                null,
                run.diagnosis() == null ? null : run.diagnosis().screenshotPath(),
                run.attemptHistory()
        ).doOnNext(enriched -> {
            run.recordAttemptFailure(message, enriched.diagnosis());
            emit(run, "PIPELINE_DIAGNOSIS", enriched.diagnosis().whyFailed(), Map.of(
                    "category", enriched.diagnosis().category(),
                    "rootCause", enriched.diagnosis().rootCause() == null ? "" : enriched.diagnosis().rootCause(),
                    "aiConfidence", enriched.diagnosis().aiConfidence() == null ? 0 : enriched.diagnosis().aiConfidence(),
                    "requiresSourceFix", enriched.diagnosis().requiresSourceFix()
            ));
        });
    }

    private Mono<Void> diagnoseAndFinish(PipelineRun run, String stage, String message) {
        return failureAwareRecoveryService.enrich(
                        run, stage, message, extractExpected(message), message,
                        null, null, null, null, run.attemptHistory())
                .doOnNext(enriched -> {
                    String category = enriched.diagnosis() == null ? "" : enriched.diagnosis().category();
                    boolean preBrowser = "UNDERSTAND".equalsIgnoreCase(stage)
                            || "INTENT_VALIDATION".equals(category);
                    if (preBrowser) {
                        run.putDetail("failureStage", "INTENT");
                        run.putDetail("failureCategory", "INTENT_VALIDATION");
                        run.putDetail("browserExecuted", false);
                        run.putDetail("assertionExecuted", false);
                    }
                    run.markFail(message, enriched.diagnosis());
                    if (enriched.sourceFix() != null) {
                        run.setSourceFix(enriched.sourceFix());
                    }
                    emit(run, "PIPELINE_FAILED", message, Map.of(
                            "status", PipelineRun.STATUS_FAIL,
                            "category", category,
                            "failureStage", preBrowser ? "INTENT" : stage,
                            "browserExecuted", false
                    ));
                })
                .then();
    }

    /** @return PASS | FAIL | BLOCKED | STOPPED */
    private Mono<String> oneAttempt(PipelineRun run, PipelineStartRequest request, UUID testCaseId) {
        throwIfStopped(run);
        return generate(run, testCaseId)
                .flatMap(generated -> {
                    throwIfStopped(run);
                    boolean skipExecution = Boolean.TRUE.equals(request.skipExecution());
                    return validate(run, testCaseId)
                            .flatMap(validation -> {
                                throwIfStopped(run);
                                if (!isPassed(validation.status())) {
                                    FailureDiagnosis diagnosis = diagnostician.diagnose(
                                            "VALIDATE",
                                            validation.errorMessage() == null
                                                    ? "Independent validation did not pass"
                                                    : validation.errorMessage(),
                                            run.attempt(),
                                            run.maxAttempts());
                                    run.putDetail("lastFailedStage", "VALIDATE");
                                    run.recordAttemptFailure("Independent validation failed", diagnosis);
                                    emit(run, "PIPELINE_STAGE_FAILED", "Validation failed", Map.of(
                                            "validationRunId", validation.id().toString(),
                                            "category", diagnosis.category()
                                    ));
                                    return Mono.just("FAIL");
                                }
                                if (skipExecution) {
                                    // VALIDATED_NOT_EXECUTED is not a production/demo PIPELINE_PASSED.
                                    run.markValidatedNotExecuted(
                                            "Generated and independently validated; final browser execution was skipped");
                                    emit(run, "PIPELINE_VALIDATED_NOT_EXECUTED",
                                            "Validated without final execution", Map.of(
                                            "status", PipelineRun.STATUS_VALIDATED_NOT_EXECUTED,
                                            "qualityGate", "PASSED",
                                            "validation", "PASSED",
                                            "execution", "SKIPPED",
                                            "pipelinePass", false
                                    ));
                                    return Mono.just(PipelineRun.STATUS_VALIDATED_NOT_EXECUTED);
                                }
                                return execute(run, testCaseId, request)
                                        .map(execution -> {
                                            if ("STOPPED".equalsIgnoreCase(execution.status())
                                                    || run.isStopRequested()) {
                                                if (!run.isTerminal()) {
                                                    run.markStopped();
                                                }
                                                return "STOPPED";
                                            }
                                            if (!isPassed(execution.status())) {
                                                FailureDiagnosis diagnosis = diagnostician.diagnose(
                                                        "EXECUTE",
                                                        execution.errorMessage() == null
                                                                ? "Final browser execution did not pass"
                                                                : execution.errorMessage(),
                                                        run.attempt(),
                                                        run.maxAttempts());
                                                run.putDetail("lastFailedStage", "EXECUTE");
                                                run.recordAttemptFailure("Final execution failed", diagnosis);
                                                emit(run, "PIPELINE_STAGE_FAILED", "Execution failed", Map.of(
                                                        "executionRunId", execution.id().toString(),
                                                        "category", diagnosis.category()
                                                ));
                                                return "FAIL";
                                            }
                                            run.markPass("Browser executed, test generated, and independently validated");
                                            emit(run, "PIPELINE_PASSED", "Test ready", Map.of(
                                                    "status", PipelineRun.STATUS_PASS,
                                                    "validation", "PASSED",
                                                    "execution", "PASSED"
                                            ));
                                            return "PASS";
                                        });
                            });
                })
                .onErrorResume(error -> {
                    if (run.isStopRequested() || safe(error).toLowerCase(Locale.ROOT).contains("stopped")) {
                        if (!run.isTerminal()) {
                            run.markStopped();
                        }
                        return Mono.just("STOPPED");
                    }
                    FailureDiagnosis diagnosis = diagnostician.diagnose(
                            run.stage() == null ? "GENERATE" : run.stage().name(),
                            safe(error),
                            run.attempt(),
                            run.maxAttempts());
                    run.putDetail("lastFailedStage", run.stage() == null ? "GENERATE" : run.stage().name());
                    run.recordAttemptFailure(safe(error), diagnosis);
                    emit(run, "PIPELINE_STAGE_FAILED", safe(error), Map.of(
                            "category", diagnosis.category(),
                            "attempt", run.attempt()
                    ));
                    return Mono.just("FAIL");
                });
    }

    private Mono<TestCaseResponse> generate(PipelineRun run, UUID testCaseId) {
        run.advance(PipelineStage.GENERATE, "Opening website");
        emit(run, "PIPELINE_STAGE", "Opening website", Map.of("userStage", "Opening website"));

        GenerationRun generationRun = GenerationRun.start(testCaseId);
        generationRunRegistry.register(generationRun);
        run.setGenerationRunId(generationRun.id());
        com.smartqa.event.RunCorrelation.set(run.id(), generationRun.id(), testCaseId);
        TraceLogger.info("PIPELINE", "GENERATION_RUN_ID_ASSIGNED", "Generation run id assigned", TraceMeta.of(
                "pipelineId", run.id().toString(),
                "generationRunId", generationRun.id().toString(),
                "testCaseId", testCaseId.toString(),
                "traceId", storedTrace(run)
        ));

        return generationService.generate(testCaseId, generationRun.id())
                .contextWrite(ctx -> com.smartqa.event.RunCorrelation.writeContext(
                        ctx, run.id(), generationRun.id(), testCaseId))
                .doOnSubscribe(ignored -> {
                    emit(run, "EXECUTION_PATH", "Generation executes semantic Intent via Playwright",
                            Map.of("path", "SEMANTIC_INTENT", "stage", "GENERATE"));
                    emit(run, "PIPELINE_STAGE", "Finding elements",
                            Map.of("userStage", "Finding elements"));
                })
                .doOnSuccess(ignored -> {
                    generationRunRegistry.update(generationRun.complete("SUCCESS"));
                    run.advance(PipelineStage.QUALITY_GATE, "Validating test");
                    emit(run, "PIPELINE_STAGE", "Validating test",
                            Map.of("userStage", "Validating test"));
                })
                .doOnError(error -> generationRunRegistry.update(generationRun.fail(safe(error))));
    }

    private Mono<ValidationRunResponse> validate(PipelineRun run, UUID testCaseId) {
        run.advance(PipelineStage.VALIDATE, "Validating test");
        emit(run, "PIPELINE_STAGE", "Validating test", Map.of("userStage", "Validating test"));
        emit(run, "EXECUTION_PATH", "Independent Validator executes generated Playwright Java",
                Map.of("path", "GENERATED_CODE", "stage", "VALIDATE"));
        return validator.validateAndAwait(testCaseId)
                .doOnNext(result -> run.setValidationRunId(result.id()));
    }

    private Mono<ExecutionRunResponse> execute(PipelineRun run, UUID testCaseId, PipelineStartRequest request) {
        run.advance(PipelineStage.EXECUTE, "Final result");
        emit(run, "PIPELINE_STAGE", "Running final browser verification",
                Map.of("userStage", "Final result"));
        emit(run, "EXECUTION_PATH", "Final execution reruns semantic Intent via Playwright",
                Map.of("path", "SEMANTIC_INTENT", "stage", "EXECUTE"));
        Boolean headless = request.headless();
        String browserMode = request.browserMode();
        if (headless == null && browserMode != null) {
            headless = !"headed".equalsIgnoreCase(browserMode.trim());
        }
        if (headless == null) {
            headless = properties.getBrowser().isHeadless();
        }
        ExecutionStartRequest startRequest = new ExecutionStartRequest(
                "PLAYWRIGHT_JAVA",
                headless ? "headless" : "headed",
                headless
        );
        return executionService.executeAndAwait(testCaseId, startRequest)
                .doOnNext(result -> run.setExecutionRunId(result.id()));
    }

    private Mono<PipelineRunResponse> enrich(PipelineRun run) {
        if (run.testCaseId() == null) {
            return Mono.just(PipelineRunResponse.from(run));
        }
        return testCaseService.get(run.testCaseId())
                .map(testCase -> {
                    List<ClarificationQuestion> clarifications = List.of();
                    IntentContract contract = intentService.readContract(testCase.intentContract());
                    if (contract != null && contract.clarifications() != null) {
                        clarifications = contract.clarifications();
                    }
                    return PipelineRunResponse.from(run, testCase, clarifications);
                })
                .defaultIfEmpty(PipelineRunResponse.from(run));
    }

    private void emit(PipelineRun run, String type, String message, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        if (details != null) {
            payload.putAll(details);
        }
        payload.put("pipelineId", run.id().toString());
        payload.put("pipelineRunId", run.id().toString());
        payload.put("generationRunId", run.generationRunId() == null ? null : run.generationRunId().toString());
        bindTrace(run, storedTrace(run), null);
        RunCorrelation.set(run.id(), run.generationRunId(), run.testCaseId());
        if (run.testCaseId() != null) {
            payload.put("testCaseId", run.testCaseId().toString());
        }
        if (run.executionRunId() != null) {
            payload.put("executionRunId", run.executionRunId().toString());
        }
        if (run.validationRunId() != null) {
            payload.put("validationRunId", run.validationRunId().toString());
        }
        String traceId = storedTrace(run);
        if (traceId == null) {
            String current = TraceContext.currentOrNull();
            if (current != null && !current.isBlank() && !TraceId.UNKNOWN.equals(current)) {
                traceId = current;
            }
        }
        if (traceId != null && !traceId.isBlank() && !TraceId.UNKNOWN.equals(traceId)) {
            payload.put("traceId", traceId);
            TraceContext.set(traceId);
        }
        payload.put("stage", run.stage() == null ? null : run.stage().name());
        payload.put("userStage", run.userStageLabel());
        payload.put("userStage", run.userStageLabel());
        payload.put("attempt", run.attempt());
        payload.put("status", run.status());
        eventHub.emit(
                ProgressEventHub.pipelineChannel(run.id()),
                ProgressEvent.generation(type, message, run.testCaseId(), payload)
        );
    }

    private static String storedTrace(PipelineRun run) {
        Object stored = run.details() == null ? null : run.details().get("traceId");
        if (stored instanceof String s && !s.isBlank() && !TraceId.UNKNOWN.equals(s)) {
            return s;
        }
        return null;
    }

    private static void bindTrace(PipelineRun run, String boundTrace, reactor.util.context.ContextView contextView) {
        String traceId = boundTrace;
        if (traceId == null || traceId.isBlank() || TraceId.UNKNOWN.equals(traceId)) {
            traceId = storedTrace(run);
        }
        if (traceId != null && !traceId.isBlank() && !TraceId.UNKNOWN.equals(traceId)) {
            TraceContext.set(traceId);
        }
        if (contextView != null) {
            RunCorrelation.applyContext(contextView);
        }
        RunCorrelation.set(run.id(), run.generationRunId(), run.testCaseId());
    }

    private void throwIfStopped(PipelineRun run) {
        if (run.isStopRequested()) {
            throw new SmartQaException(ErrorCode.VALIDATION_FAILED, "Pipeline stopped");
        }
    }

    private static boolean isPassed(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        return "PASSED".equals(s) || "PASS".equals(s) || "SUCCESS".equals(s);
    }

    private static String extractExpected(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = EXPECTED_TEXT.matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String safe(Throwable error) {
        if (error == null) {
            return "Unknown pipeline failure";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        return error.getClass().getSimpleName();
    }
}
