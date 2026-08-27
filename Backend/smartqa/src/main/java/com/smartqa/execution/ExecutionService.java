package com.smartqa.execution;

import com.smartqa.common.config.BlockingWorkConfig;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.ResourceNotFoundException;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.browser.BrowserExecutionOptions;
import com.smartqa.browser.BrowserExecutionProvider;
import com.smartqa.browser.ExecutionPlan;
import com.smartqa.intent.IntentContract;
import com.smartqa.intent.IntentPlanDag;
import com.smartqa.intent.IntentScenario;
import com.smartqa.intent.IntentService;
import com.smartqa.intent.IntentStep;
import com.smartqa.project.ProjectRepository;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.execution.cancel.CancellationToken;
import com.smartqa.execution.cancel.ExecutionCancelledException;
import com.smartqa.execution.cancel.ExecutionCancellationRegistry;
import com.smartqa.testcase.TestCase;
import com.smartqa.testcase.TestCaseService;
import com.smartqa.testcase.TestCaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final TestCaseService testCaseService;
    private final ProjectRepository projectRepository;
    private final IntentService intentService;
    private final BrowserExecutionProvider browserExecutionProvider;
    private final ExecutionRunRepository runRepository;
    private final ProgressEventHub eventHub;
    private final SmartQaProperties properties;
    private final Scheduler blockingScheduler;
    private final ExecutionCancellationRegistry cancellationRegistry;
    private final ConcurrentHashMap<UUID, Thread> running = new ConcurrentHashMap<>();

    public ExecutionService(
            TestCaseService testCaseService,
            ProjectRepository projectRepository,
            IntentService intentService,
            BrowserExecutionProvider browserExecutionProvider,
            ExecutionRunRepository runRepository,
            ProgressEventHub eventHub,
            SmartQaProperties properties,
            @Qualifier(BlockingWorkConfig.BLOCKING_SCHEDULER) Scheduler blockingScheduler,
            ExecutionCancellationRegistry cancellationRegistry) {
        this.testCaseService = testCaseService;
        this.projectRepository = projectRepository;
        this.intentService = intentService;
        this.browserExecutionProvider = browserExecutionProvider;
        this.runRepository = runRepository;
        this.eventHub = eventHub;
        this.properties = properties;
        this.blockingScheduler = blockingScheduler;
        this.cancellationRegistry = cancellationRegistry;
    }

    public Mono<ExecutionRunResponse> execute(UUID testCaseId, ExecutionStartRequest request) {
        return testCaseService.requireEntity(testCaseId)
                .flatMap(testCase -> {
                    if (testCase.getIntentContract() == null || testCase.getIntentContract().isBlank()) {
                        return Mono.error(new SmartQaException(
                                ErrorCode.VALIDATION_FAILED,
                                "Analyze and generate a test before executing"));
                    }
                    ExecutionRun run = new ExecutionRun();
                    run.setTestCaseId(testCaseId);
                    run.setStatus("RUNNING");
                    run.setCreatedAt(LocalDateTime.now());
                    run.setStartedAt(LocalDateTime.now());
                    return runRepository.save(run)
                            .doOnSuccess(saved -> runBrowserExecution(testCase, saved, request)
                                    .subscribe(
                                            result -> log.info("execution_async_complete runId={} status={}", saved.getId(), result.status()),
                                            error -> log.error("execution_async_error runId={}", saved.getId(), error)
                                    ))
                            .map(ExecutionRunResponse::from);
                });
    }

    /**
     * Starts execution and waits for the live browser run to finish.
     * Used by the autonomous Generate & Validate pipeline.
     */
    public Mono<ExecutionRunResponse> executeAndAwait(UUID testCaseId, ExecutionStartRequest request) {
        return testCaseService.requireEntity(testCaseId)
                .flatMap(testCase -> {
                    if (testCase.getIntentContract() == null || testCase.getIntentContract().isBlank()) {
                        return Mono.error(new SmartQaException(
                                ErrorCode.VALIDATION_FAILED,
                                "Analyze and generate a test before executing"));
                    }
                    ExecutionRun run = new ExecutionRun();
                    run.setTestCaseId(testCaseId);
                    run.setStatus("RUNNING");
                    run.setCreatedAt(LocalDateTime.now());
                    run.setStartedAt(LocalDateTime.now());
                    return runRepository.save(run)
                            .flatMap(saved -> runBrowserExecution(testCase, saved, request));
                });
    }

    public Mono<ExecutionRunResponse> get(UUID runId) {
        return runRepository.findById(runId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Execution run not found: " + runId)))
                .map(ExecutionRunResponse::from);
    }

    public Mono<ExecutionRunResponse> stop(UUID runId) {
        cancellationRegistry.requestStop(runId);
        Thread thread = running.get(runId);
        if (thread != null) {
            thread.interrupt();
        }
        return runRepository.findById(runId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Execution run not found: " + runId)))
                .flatMap(run -> {
                    if ("RUNNING".equals(run.getStatus())) {
                        run.setStatus("STOPPED");
                        run.setFinishedAt(LocalDateTime.now());
                        run.setErrorMessage("Stopped by user");
                        String channel = ProgressEventHub.executionChannel(runId);
                        eventHub.emit(channel, ProgressEvent.execution("EXECUTION_STOP_REQUESTED", "Stop requested by user", run.getTestCaseId(), runId));
                        eventHub.emit(channel, ProgressEvent.execution("EXECUTION_STOPPED", "Execution stopped", run.getTestCaseId(), runId));
                        return runRepository.save(run);
                    }
                    return Mono.just(run);
                })
                .map(ExecutionRunResponse::from);
    }

    private Mono<ExecutionRunResponse> runBrowserExecution(TestCase testCase, ExecutionRun run, ExecutionStartRequest request) {
        UUID runId = run.getId();
        UUID testCaseId = testCase.getId();
        String channel = ProgressEventHub.executionChannel(runId);
        CancellationToken token = cancellationRegistry.register(runId);
        BrowserExecutionOptions options = resolveOptions(request);
        eventHub.emit(channel, ProgressEvent.execution("EXECUTION_STARTED", "Execution started", testCaseId, runId));
        log.info("execution_started testCaseId={} runId={}", testCaseId, runId);
        TraceLogger.info("EXECUTION", "EXECUTION_STARTED", "Execution started", TraceMeta.of(
                "testCaseId", testCaseId.toString(),
                "runId", runId.toString(),
                "codeLength", testCase.getGeneratedCode() == null ? 0 : testCase.getGeneratedCode().length()
        ));
        String traceId = TraceContext.current();
        testCase.setStatus(TestCaseStatus.RUNNING);
        return testCaseService.saveEntity(testCase)
                .then(Mono.fromCallable(() -> TraceContext.callChecked(traceId, () -> {
                    running.put(runId, Thread.currentThread());
                    token.throwIfStopped();
                    Path screenshotDir = Path.of(properties.getScreenshots().getBaseDir(), testCaseId.toString(), runId.toString());
                    Files.createDirectories(screenshotDir);
                    eventHub.emit(channel, ProgressEvent.execution("SCENARIO_STARTED", "Running browser execution", testCaseId, runId));
                    ExecutionPlan plan = buildExecutionPlan(testCase, runId);
                    BrowserExecutionOptions effective = options;
                    browserExecutionProvider.execute(plan, event -> eventHub.emit(channel, withRunContext(event, runId, traceId)), token, effective);
                    return true;
                })).subscribeOn(blockingScheduler))
                .flatMap(passed -> {
                    run.setStatus(passed ? "PASSED" : "FAILED");
                    run.setExitCode(passed ? 0 : 1);
                    run.setStdout(null);
                    run.setStderr(null);
                    run.setErrorMessage(null);
                    run.setDurationMs(java.time.Duration.between(run.getStartedAt(), LocalDateTime.now()).toMillis());
                    run.setFinishedAt(LocalDateTime.now());
                    run.setScenarioResults(passed ? "passed" : "failed");
                    testCase.setStatus(passed ? TestCaseStatus.PASSED : TestCaseStatus.FAILED);
                    eventHub.emit(channel, ProgressEvent.execution(
                            passed ? "EXECUTION_COMPLETED" : "EXECUTION_FAILED",
                            passed ? "Execution completed" : "Execution failed",
                            testCaseId,
                            runId,
                            Map.of("exitCode", run.getExitCode(), "durationMs", run.getDurationMs())
                    ));
                    log.info("execution_completed testCaseId={} runId={} status={}", testCaseId, runId, run.getStatus());
                    TraceLogger.info("EXECUTION", "EXECUTION_STEP_COMPLETED", "Isolated JVM finished", TraceMeta.of(
                            "step", 1,
                            "status", passed ? "PASS" : "FAIL",
                            "durationMs", run.getDurationMs()
                    ));
                    TraceLogger.info("EXECUTION", "EXECUTION_COMPLETED", "Execution finished",
                            run.getDurationMs(),
                            TraceMeta.of(
                                    "status", passed ? "PASSED" : "FAILED",
                                    "passedSteps", passed ? 1 : 0,
                                    "failedSteps", passed ? 0 : 1,
                                    "exitCode", run.getExitCode()
                            ));
                    TraceLogger.info("HTTP", "TRACE_END", "Execution finished", TraceMeta.of("status", passed ? "PASSED" : "FAILED"));
                    return testCaseService.saveEntity(testCase).then(runRepository.save(run));
                })
                .onErrorResume(error -> {
                    boolean cancelled = error instanceof ExecutionCancelledException;
                    run.setStatus(cancelled ? "STOPPED" : "ERROR");
                    run.setErrorMessage(cancelled ? "Stopped by user" : error.getMessage());
                    run.setExitCode(cancelled ? 130 : 1);
                    run.setDurationMs(java.time.Duration.between(run.getStartedAt(), LocalDateTime.now()).toMillis());
                    run.setFinishedAt(LocalDateTime.now());
                    testCase.setStatus(cancelled ? TestCaseStatus.FAILED : TestCaseStatus.ERROR);
                    String eventType = cancelled ? "EXECUTION_STOPPED" : "EXECUTION_FAILED";
                    eventHub.emit(channel, ProgressEvent.execution(eventType, safe(error), testCaseId, runId));
                    if (cancelled) {
                        log.info("execution_stopped testCaseId={} runId={}", testCaseId, runId);
                    } else {
                        log.error("execution_failed testCaseId={} runId={}", testCaseId, runId, error);
                    }
                    TraceLogger.error("EXECUTION", eventType, cancelled ? "Execution stopped" : "Execution failed", error, null, TraceMeta.of(
                            "testCaseId", testCaseId.toString(),
                            "runId", runId.toString()
                    ));
                    TraceLogger.info("HTTP", "TRACE_END", "Execution finished", TraceMeta.of("status", cancelled ? "STOPPED" : "FAILED"));
                    return testCaseService.saveEntity(testCase).then(runRepository.save(run));
                })
                .doFinally(signal -> {
                    running.remove(runId);
                    cancellationRegistry.unregister(runId);
                })
                .map(ExecutionRunResponse::from);
    }

    private ExecutionPlan buildExecutionPlan(TestCase testCase, UUID executionRunId) {
        IntentContract intent = intentService.readContract(testCase.getIntentContract());
        if (intent == null) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "Intent contract is missing");
        }
        return projectRepository.findById(testCase.getProjectId())
                .switchIfEmpty(Mono.error(new SmartQaException(ErrorCode.RESOURCE_NOT_FOUND, "Project not found")))
                .map(project -> {
                    List<ExecutionPlan.PlannedStep> steps = new ArrayList<>();
                    if (intent.scenarios() != null) {
                        for (IntentScenario scenario : intent.scenarios()) {
                            if (scenario.steps() == null) continue;
                            for (IntentStep step : IntentPlanDag.inferSequentialDependsOn(scenario.steps())) {
                                steps.add(ExecutionPlan.PlannedStep.from(step));
                            }
                        }
                    }
                    return new ExecutionPlan(testCase.getId(), testCase.getName(), project.getApplicationUrl(), steps, executionRunId);
                })
                .blockOptional()
                .orElseThrow(() -> new SmartQaException(ErrorCode.RESOURCE_NOT_FOUND, "Project not found"));
    }

    private BrowserExecutionOptions resolveOptions(ExecutionStartRequest request) {
        if (request == null) {
            return new BrowserExecutionOptions(properties.getBrowser().getProvider(), properties.getBrowser().isHeadless());
        }
        Boolean headless = request.headless();
        if (headless == null && request.browserMode() != null) {
            String mode = request.browserMode().trim().toLowerCase(Locale.ROOT);
            headless = !"headed".equals(mode);
        }
        return new BrowserExecutionOptions(request.executionProvider(), headless);
    }

    private ProgressEvent withRunContext(ProgressEvent event, UUID runId, String traceId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (event.details() != null) {
            details.putAll(event.details());
        }
        details.put("traceId", traceId);
        details.put("runId", runId.toString());
        return ProgressEvent.rich(
                event.type(),
                event.message(),
                event.testCaseId(),
                runId,
                details,
                event.stepNumber(),
                event.totalSteps(),
                event.currentUrl(),
                event.pageTitle(),
                event.executionProvider(),
                event.screenshotId()
        );
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 20_000 ? value.substring(value.length() - 20_000) : value;
    }

    private static String safe(Throwable error) {
        return error.getMessage() == null ? "Execution failed" : error.getMessage();
    }
}
