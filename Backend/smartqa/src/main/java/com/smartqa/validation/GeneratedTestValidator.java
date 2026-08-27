package com.smartqa.validation;

import com.smartqa.common.config.BlockingWorkConfig;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.ResourceNotFoundException;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import com.smartqa.execution.IsolatedTestExecutor;
import com.smartqa.testcase.TestCase;
import com.smartqa.testcase.TestCaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Independent validator that executes the actual generated test against the real application.
 * This is separate from QualityGateService (static analysis) and GenerationService (code generation).
 * Only this component can declare VALIDATION_PASSED after real browser execution.
 */
@Service
public class GeneratedTestValidator {

    private static final Logger log = LoggerFactory.getLogger(GeneratedTestValidator.class);
    private static final Pattern STEP_PATTERN = Pattern.compile("STEP_(\\d+)_(PASS|FAIL)(?:\\s+(.*))?");

    private final TestCaseService testCaseService;
    private final ValidationRunRepository validationRunRepository;
    private final IsolatedTestExecutor isolatedTestExecutor;
    private final ProgressEventHub eventHub;
    private final SmartQaProperties properties;
    private final Scheduler blockingScheduler;

    public GeneratedTestValidator(
            TestCaseService testCaseService,
            ValidationRunRepository validationRunRepository,
            IsolatedTestExecutor isolatedTestExecutor,
            ProgressEventHub eventHub,
            SmartQaProperties properties,
            @Qualifier(BlockingWorkConfig.BLOCKING_SCHEDULER) Scheduler blockingScheduler) {
        this.testCaseService = testCaseService;
        this.validationRunRepository = validationRunRepository;
        this.isolatedTestExecutor = isolatedTestExecutor;
        this.eventHub = eventHub;
        this.properties = properties;
        this.blockingScheduler = blockingScheduler;
    }

    public Mono<ValidationRunResponse> validate(UUID testCaseId) {
        return testCaseService.requireEntity(testCaseId)
                .flatMap(testCase -> {
                    if (testCase.getGeneratedCode() == null || testCase.getGeneratedCode().isBlank()) {
                        return Mono.error(new SmartQaException(
                                ErrorCode.VALIDATION_FAILED,
                                "No generated code to validate. Generate a test first."));
                    }
                    return validationRunRepository.countByTestCaseId(testCaseId)
                            .flatMap(count -> {
                                ValidationRun run = new ValidationRun();
                                run.setTestCaseId(testCaseId);
                                run.setStatus("RUNNING");
                                run.setAttemptNumber(count.intValue() + 1);
                                run.setCreatedAt(LocalDateTime.now());
                                run.setStartedAt(LocalDateTime.now());
                                return validationRunRepository.save(run)
                                        .doOnSuccess(saved -> executeValidation(testCase, saved)
                                                .subscribe(
                                                        result -> log.info("validation_complete testCaseId={} validationRunId={} status={}",
                                                                testCaseId, saved.getId(), result.status()),
                                                        error -> log.error("validation_error testCaseId={} validationRunId={}",
                                                                testCaseId, saved.getId(), error)
                                                ))
                                        .map(ValidationRunResponse::from);
                            });
                });
    }

    /**
     * Starts validation and waits for the independent browser run to finish.
     * Used by the autonomous Generate & Validate pipeline.
     */
    public Mono<ValidationRunResponse> validateAndAwait(UUID testCaseId) {
        return testCaseService.requireEntity(testCaseId)
                .flatMap(testCase -> {
                    if (testCase.getGeneratedCode() == null || testCase.getGeneratedCode().isBlank()) {
                        return Mono.error(new SmartQaException(
                                ErrorCode.VALIDATION_FAILED,
                                "No generated code to validate. Generate a test first."));
                    }
                    return validationRunRepository.countByTestCaseId(testCaseId)
                            .flatMap(count -> {
                                ValidationRun run = new ValidationRun();
                                run.setTestCaseId(testCaseId);
                                run.setStatus("RUNNING");
                                run.setAttemptNumber(count.intValue() + 1);
                                run.setCreatedAt(LocalDateTime.now());
                                run.setStartedAt(LocalDateTime.now());
                                return validationRunRepository.save(run)
                                        .flatMap(saved -> executeValidation(testCase, saved));
                            });
                });
    }

    public Mono<ValidationRunResponse> get(UUID validationRunId) {
        return validationRunRepository.findById(validationRunId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Validation run not found: " + validationRunId)))
                .map(ValidationRunResponse::from);
    }

    public Flux<ValidationRunResponse> history(UUID testCaseId) {
        return validationRunRepository.findByTestCaseIdOrderByAttemptNumberDesc(testCaseId)
                .map(ValidationRunResponse::from);
    }

    private Mono<ValidationRunResponse> executeValidation(TestCase testCase, ValidationRun run) {
        UUID validationRunId = run.getId();
        UUID testCaseId = testCase.getId();
        String channel = ProgressEventHub.validationChannel(validationRunId);
        long started = System.currentTimeMillis();

        eventHub.emit(channel, ProgressEvent.execution("VALIDATION_STARTED",
                "Independent Validator executing generated Playwright Java",
                testCaseId, validationRunId,
                Map.of("path", "GENERATED_CODE", "stage", "VALIDATE", "executionPath", "GENERATED_CODE")));
        log.info("validation_started testCaseId={} validationRunId={} attempt={}",
                testCaseId, validationRunId, run.getAttemptNumber());
        TraceLogger.info("VALIDATOR", "VALIDATION_STARTED", "Independent validation started", TraceMeta.of(
                "testCaseId", testCaseId.toString(),
                "validationRunId", validationRunId.toString(),
                "attempt", run.getAttemptNumber()
        ));

        String traceId = TraceContext.current();
        return Mono.fromCallable(() -> TraceContext.callChecked(traceId, () -> {
                    Path screenshotDir = Path.of(properties.getScreenshots().getBaseDir(),
                            "validation", testCaseId.toString(), validationRunId.toString());
                    Files.createDirectories(screenshotDir);

                    IsolatedTestExecutor.ExecutionResult result =
                            isolatedTestExecutor.run(testCase.getGeneratedCode(), screenshotDir);

                    return result;
                }))
                .subscribeOn(blockingScheduler)
                .flatMap(result -> {
                    long durationMs = System.currentTimeMillis() - started;
                    boolean passed = result.exitCode() == 0;

                    List<ValidationStepResult> steps = parseStepResults(result.stdout(), result.stderr());

                    run.setStatus(passed ? "PASSED" : "FAILED");
                    run.setDurationMs(durationMs);
                    run.setFinishedAt(LocalDateTime.now());
                    run.setStdout(trimOutput(result.stdout()));
                    run.setStderr(trimOutput(result.stderr()));
                    run.setErrorMessage(result.errorMessage());

                    if (passed) {
                        ValidationResult vr = ValidationResult.pass(steps, durationMs, run.getAttemptNumber());
                        run.setResult(serializeResult(vr));
                        eventHub.emit(channel, ProgressEvent.execution("VALIDATION_COMPLETED",
                                "Validation passed", testCaseId, validationRunId,
                                Map.of("status", "PASSED", "steps", steps.size())));
                    } else {
                        int failedStep = findFailedStep(steps);
                        String failedAction = failedStep > 0 && failedStep <= steps.size()
                                ? steps.get(failedStep - 1).action() : null;
                        ValidationResult vr = ValidationResult.fail(steps, failedStep, failedAction,
                                result.errorMessage(), null, durationMs, run.getAttemptNumber());
                        run.setResult(serializeResult(vr));
                        eventHub.emit(channel, ProgressEvent.execution("VALIDATION_COMPLETED",
                                "Validation failed", testCaseId, validationRunId,
                                Map.of("status", "FAILED", "failedStep", failedStep,
                                        "error", nullToEmpty(result.errorMessage()))));
                    }

                    TraceLogger.info("VALIDATOR", "VALIDATION_COMPLETED", "Validation finished",
                            durationMs, TraceMeta.of(
                                    "status", passed ? "PASSED" : "FAILED",
                                    "attempt", run.getAttemptNumber(),
                                    "exitCode", result.exitCode()
                            ));

                    return validationRunRepository.save(run);
                })
                .onErrorResume(error -> {
                    long durationMs = System.currentTimeMillis() - started;
                    run.setStatus("ERROR");
                    run.setErrorMessage(error.getMessage());
                    run.setFinishedAt(LocalDateTime.now());
                    run.setDurationMs(durationMs);
                    eventHub.emit(channel, ProgressEvent.execution("VALIDATION_COMPLETED",
                            "Validation error: " + safeMessage(error), testCaseId, validationRunId));
                    log.error("validation_failed testCaseId={} validationRunId={}",
                            testCaseId, validationRunId, error);
                    return validationRunRepository.save(run);
                })
                .map(ValidationRunResponse::from);
    }

    private List<ValidationStepResult> parseStepResults(String stdout, String stderr) {
        List<ValidationStepResult> steps = new ArrayList<>();
        String combined = (stdout == null ? "" : stdout) + "\n" + (stderr == null ? "" : stderr);
        for (String line : combined.lines().toList()) {
            Matcher m = STEP_PATTERN.matcher(line.trim());
            if (m.matches()) {
                int num = Integer.parseInt(m.group(1));
                String status = "PASS".equals(m.group(2)) ? "PASSED" : "FAILED";
                String detail = m.group(3);
                steps.add(new ValidationStepResult(num, null, null, status, detail, 0));
            }
        }
        if (steps.isEmpty()) {
            boolean passed = stderr == null || !stderr.contains("FAILED") && !stderr.contains("AssertionError");
            steps.add(new ValidationStepResult(1, "test", "generated test",
                    passed ? "PASSED" : "FAILED",
                    passed ? null : firstLine(stderr), 0));
        }
        return steps;
    }

    private int findFailedStep(List<ValidationStepResult> steps) {
        for (ValidationStepResult step : steps) {
            if ("FAILED".equals(step.status())) {
                return step.stepNumber();
            }
        }
        return steps.isEmpty() ? 0 : steps.size();
    }

    private String serializeResult(ValidationResult result) {
        try {
            var mapper = new tools.jackson.databind.json.JsonMapper();
            return mapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"status\":\"" + result.status() + "\"}";
        }
    }

    private String trimOutput(String value) {
        if (value == null) return null;
        return value.length() > 20_000 ? value.substring(value.length() - 20_000) : value;
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) return null;
        return text.lines().findFirst().orElse(null);
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? "Validation failed" : error.getMessage();
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
