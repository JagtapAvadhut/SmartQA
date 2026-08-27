package com.smartqa.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory autonomous Generate & Validate pipeline run.
 * Persisted artifacts (test case, generation, validation, execution) remain in their own stores.
 */
public final class PipelineRun {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PASS = "PASS";
    /** Validation succeeded but final browser execution was intentionally skipped — not a full PIPELINE_PASSED. */
    public static final String STATUS_VALIDATED_NOT_EXECUTED = "VALIDATED_NOT_EXECUTED";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_STOPPED = "STOPPED";
    /** In-progress run abandoned after backend restart (no durable heartbeat). */
    public static final String STATUS_ABANDONED = "ABANDONED";

    private final UUID id;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile String status;
    private volatile PipelineStage stage;
    private volatile String userStageLabel;
    private volatile UUID projectId;
    private volatile UUID testCaseId;
    private volatile UUID generationRunId;
    private volatile UUID validationRunId;
    private volatile UUID executionRunId;
    private volatile String applicationUrl;
    private volatile Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile Long durationMs;
    private volatile String errorMessage;
    private volatile String finalSummary;
    private volatile FailureDiagnosis diagnosis;
    private volatile int attempt;
    private volatile int maxAttempts;
    private final List<String> userProgress = new ArrayList<>();
    private final List<String> attemptHistory = new ArrayList<>();
    private final ConcurrentHashMap<String, Object> details = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> recoveryHints = new ConcurrentHashMap<>();
    private volatile SourceFixProposal sourceFix;

    private PipelineRun(UUID id) {
        this.id = id;
        this.status = STATUS_QUEUED;
        this.stage = PipelineStage.PREFLIGHT;
        this.userStageLabel = "Preparing";
        this.attempt = 1;
        this.maxAttempts = 3;
        this.createdAt = Instant.now();
    }

    /** Rebuild from durable storage after process restart. */
    public static PipelineRun rehydrate(
            UUID id,
            String status,
            String stage,
            String userStageLabel,
            UUID projectId,
            UUID testCaseId,
            UUID generationRunId,
            UUID validationRunId,
            UUID executionRunId,
            String applicationUrl,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            String errorMessage,
            String finalSummary,
            int attempt,
            int maxAttempts) {
        PipelineRun run = new PipelineRun(id);
        run.status = status == null ? STATUS_ABANDONED : status;
        try {
            run.stage = stage == null ? PipelineStage.COMPLETE : PipelineStage.valueOf(stage);
        } catch (RuntimeException ex) {
            run.stage = PipelineStage.COMPLETE;
        }
        run.userStageLabel = userStageLabel;
        run.projectId = projectId;
        run.testCaseId = testCaseId;
        run.generationRunId = generationRunId;
        run.validationRunId = validationRunId;
        run.executionRunId = executionRunId;
        run.applicationUrl = applicationUrl;
        run.createdAt = startedAt == null ? Instant.now() : startedAt;
        run.startedAt = startedAt;
        run.finishedAt = finishedAt;
        run.durationMs = durationMs;
        run.errorMessage = errorMessage;
        run.finalSummary = finalSummary;
        run.attempt = attempt;
        run.maxAttempts = maxAttempts;
        return run;
    }

    public static PipelineRun start() {
        PipelineRun run = new PipelineRun(UUID.randomUUID());
        run.startedAt = Instant.now();
        run.status = STATUS_RUNNING;
        return run;
    }

    public UUID id() {
        return id;
    }

    public String status() {
        return status;
    }

    public PipelineStage stage() {
        return stage;
    }

    public String userStageLabel() {
        return userStageLabel;
    }

    public UUID projectId() {
        return projectId;
    }

    public UUID testCaseId() {
        return testCaseId;
    }

    public UUID generationRunId() {
        return generationRunId;
    }

    public UUID validationRunId() {
        return validationRunId;
    }

    public UUID executionRunId() {
        return executionRunId;
    }

    public String applicationUrl() {
        return applicationUrl;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public Long durationMs() {
        return durationMs;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String finalSummary() {
        return finalSummary;
    }

    public FailureDiagnosis diagnosis() {
        return diagnosis;
    }

    public SourceFixProposal sourceFix() {
        return sourceFix;
    }

    public void setSourceFix(SourceFixProposal sourceFix) {
        this.sourceFix = sourceFix;
    }

    public List<String> attemptHistory() {
        synchronized (attemptHistory) {
            return List.copyOf(attemptHistory);
        }
    }

    public void addAttemptHistory(String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        synchronized (attemptHistory) {
            attemptHistory.add(summary);
        }
    }

    public Map<String, Object> recoveryHints() {
        return Map.copyOf(recoveryHints);
    }

    public void setRecoveryHints(Map<String, Object> hints) {
        recoveryHints.clear();
        if (hints != null) {
            recoveryHints.putAll(hints);
        }
    }

    public int attempt() {
        return attempt;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public List<String> userProgress() {
        synchronized (userProgress) {
            return List.copyOf(userProgress);
        }
    }

    public Map<String, Object> details() {
        return Map.copyOf(details);
    }

    public boolean isTerminal() {
        return STATUS_PASS.equals(status)
                || STATUS_VALIDATED_NOT_EXECUTED.equals(status)
                || STATUS_FAIL.equals(status)
                || STATUS_BLOCKED.equals(status)
                || STATUS_STOPPED.equals(status)
                || STATUS_ABANDONED.equals(status);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public void bindWorkspace(UUID projectId, UUID testCaseId, String applicationUrl) {
        this.projectId = projectId;
        this.testCaseId = testCaseId;
        this.applicationUrl = applicationUrl;
    }

    public void setGenerationRunId(UUID generationRunId) {
        this.generationRunId = generationRunId;
    }

    public void setValidationRunId(UUID validationRunId) {
        this.validationRunId = validationRunId;
    }

    public void setExecutionRunId(UUID executionRunId) {
        this.executionRunId = executionRunId;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void putDetail(String key, Object value) {
        if (key != null && value != null) {
            details.put(key, value);
        }
    }

    public void advance(PipelineStage stage, String userLabel) {
        this.stage = stage;
        this.userStageLabel = userLabel;
        synchronized (userProgress) {
            if (userProgress.isEmpty() || !userProgress.get(userProgress.size() - 1).equals(userLabel)) {
                userProgress.add(userLabel);
            }
        }
    }

    public void markPass(String summary) {
        requireTransition(STATUS_PASS, STATUS_RUNNING);
        this.status = STATUS_PASS;
        this.stage = PipelineStage.COMPLETE;
        this.userStageLabel = "Test ready";
        this.finalSummary = summary;
        this.errorMessage = null;
        putDetail("qualityGate", "PASSED");
        putDetail("validation", "PASSED");
        putDetail("execution", "PASSED");
        putDetail("pipelinePass", true);
        finish();
    }

    /**
     * Independent validation passed but final execution was skipped.
     * This is NOT a full PIPELINE_PASSED / production PASS.
     */
    public void markValidatedNotExecuted(String summary) {
        this.status = STATUS_VALIDATED_NOT_EXECUTED;
        this.stage = PipelineStage.COMPLETE;
        this.userStageLabel = "Validated (not executed)";
        this.finalSummary = summary;
        this.errorMessage = null;
        putDetail("qualityGate", "PASSED");
        putDetail("validation", "PASSED");
        putDetail("execution", "SKIPPED");
        putDetail("pipelinePass", false);
        finish();
    }

    public void markFail(String message, FailureDiagnosis diagnosis) {
        requireTransition(STATUS_FAIL, STATUS_RUNNING);
        this.status = STATUS_FAIL;
        this.stage = PipelineStage.COMPLETE;
        this.userStageLabel = "Failed";
        this.errorMessage = message;
        this.diagnosis = diagnosis;
        this.finalSummary = message;
        finish();
    }

    public void markBlocked(String message, FailureDiagnosis diagnosis) {
        this.status = STATUS_BLOCKED;
        this.stage = PipelineStage.COMPLETE;
        this.userStageLabel = "Needs input";
        this.errorMessage = message;
        this.diagnosis = diagnosis;
        this.finalSummary = message;
        finish();
    }

    public void markStopped() {
        if (STATUS_STOPPED.equals(status)) {
            return;
        }
        requireTransition(STATUS_STOPPED, STATUS_RUNNING, STATUS_QUEUED);
        this.status = STATUS_STOPPED;
        this.stage = PipelineStage.COMPLETE;
        this.userStageLabel = "Stopped";
        this.errorMessage = "Stopped by user";
        this.finalSummary = "Pipeline stopped";
        finish();
    }

    public void markAbandoned(String message) {
        this.status = STATUS_ABANDONED;
        this.stage = PipelineStage.COMPLETE;
        this.userStageLabel = "Abandoned";
        this.errorMessage = message;
        this.finalSummary = message;
        putDetail("recoverable", false);
        putDetail("reason", "BACKEND_RESTART");
        finish();
    }

    /**
     * Soft-fail for a single attempt without finishing the pipeline.
     * Used so the orchestrator can decide on bounded auto-retry.
     */
    public void recordAttemptFailure(String message, FailureDiagnosis diagnosis) {
        this.errorMessage = message;
        this.diagnosis = diagnosis;
        this.status = STATUS_RUNNING;
        this.userStageLabel = "Recovering";
    }

    public void reopenForRetry() {
        requireTransition(STATUS_RUNNING, STATUS_RUNNING, STATUS_FAIL);
        this.status = STATUS_RUNNING;
        this.finishedAt = null;
        this.durationMs = null;
        this.finalSummary = null;
        this.userStageLabel = "Recovering and retrying";
        this.stage = PipelineStage.RECOVER;
    }

    private void requireTransition(String target, String... allowedFrom) {
        for (String allowed : allowedFrom) {
            if (allowed.equals(status)) {
                return;
            }
        }
        throw new IllegalStateException("Illegal pipeline transition " + status + " -> " + target);
    }

    private void finish() {
        this.finishedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        }
    }
}
