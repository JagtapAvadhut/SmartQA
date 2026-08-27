package com.smartqa.pipeline;

import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Persists business-important pipeline run state for restart survival.
 * SSE/progress hubs remain ephemeral.
 */
@Service
public class PipelineRunPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunPersistenceService.class);

    private final PipelineRunRepository repository;
    private final JsonMapper jsonMapper;

    public PipelineRunPersistenceService(PipelineRunRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public void persistAsync(PipelineRun run) {
        if (run == null) {
            return;
        }
        toEntity(run)
                .flatMap(repository::save)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        saved -> log.debug("pipeline_run_persisted id={} status={}", saved.getId(), saved.getStatus()),
                        error -> log.warn("pipeline_run_persist_failed id={} err={}", run.id(), error.toString())
                );
    }

    public Mono<PipelineRun> findById(UUID id) {
        return repository.findById(id).map(this::fromEntity);
    }

    public Mono<PipelineRun> findLatestByTestCaseId(UUID testCaseId) {
        if (testCaseId == null) {
            return Mono.empty();
        }
        return repository.findFirstByTestCaseIdOrderByStartedAtDesc(testCaseId).map(this::fromEntity);
    }

    private Mono<PipelineRunEntity> toEntity(PipelineRun run) {
        return Mono.fromCallable(() -> {
            PipelineRunEntity entity = new PipelineRunEntity();
            entity.setId(run.id());
            entity.setStatus(run.status());
            entity.setStage(run.stage() == null ? null : run.stage().name());
            entity.setUserStageLabel(run.userStageLabel());
            entity.setProjectId(run.projectId());
            entity.setTestCaseId(run.testCaseId());
            entity.setGenerationRunId(run.generationRunId());
            entity.setValidationRunId(run.validationRunId());
            entity.setExecutionRunId(run.executionRunId());
            entity.setApplicationUrl(run.applicationUrl());
            Object stored = run.details() == null ? null : run.details().get("traceId");
            String traceId = stored instanceof String s && !s.isBlank() && !TraceId.UNKNOWN.equals(s)
                    ? s
                    : TraceContext.currentOrNull();
            if (traceId != null && (traceId.isBlank() || TraceId.UNKNOWN.equals(traceId))) {
                traceId = null;
            }
            entity.setTraceId(traceId);
            entity.setStartedAt(toLocal(run.startedAt()));
            entity.setFinishedAt(toLocal(run.finishedAt()));
            entity.setDurationMs(run.durationMs());
            entity.setErrorMessage(run.errorMessage());
            entity.setFinalSummary(run.finalSummary());
            entity.setAttempt(run.attempt());
            entity.setMaxAttempts(run.maxAttempts());
            try {
                entity.setDetailsJson(jsonMapper.writeValueAsString(run.details()));
            } catch (Exception ex) {
                entity.setDetailsJson("{}");
            }
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            entity.setCreatedAt(entity.getStartedAt() == null ? now : entity.getStartedAt());
            entity.setUpdatedAt(now);
            // Stale in-progress after process death is reported as ABANDONED when reloaded later.
            if (!run.isTerminal() && run.finishedAt() == null) {
                // Keep RUNNING in live process; abandon only on cold reload via fromEntity.
            }
            return entity;
        });
    }

    private PipelineRun fromEntity(PipelineRunEntity entity) {
        PipelineRun run = PipelineRun.rehydrate(
                entity.getId(),
                entity.getStatus(),
                entity.getStage(),
                entity.getUserStageLabel(),
                entity.getProjectId(),
                entity.getTestCaseId(),
                entity.getGenerationRunId(),
                entity.getValidationRunId(),
                entity.getExecutionRunId(),
                entity.getApplicationUrl(),
                toInstant(entity.getStartedAt()),
                toInstant(entity.getFinishedAt()),
                entity.getDurationMs(),
                entity.getErrorMessage(),
                entity.getFinalSummary(),
                entity.getAttempt(),
                entity.getMaxAttempts()
        );
        if (!run.isTerminal()
                && (PipelineRun.STATUS_RUNNING.equals(entity.getStatus())
                || PipelineRun.STATUS_QUEUED.equals(entity.getStatus()))) {
            // Process-local progress is gone; mark abandoned for honesty after restart.
            run.markAbandoned("Pipeline was interrupted by backend restart");
        }
        try {
            if (entity.getDetailsJson() != null && !entity.getDetailsJson().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> details = jsonMapper.readValue(entity.getDetailsJson(), Map.class);
                if (details != null) {
                    details.forEach(run::putDetail);
                }
            }
        } catch (Exception ignored) {
        }
        if (entity.getTraceId() != null && !entity.getTraceId().isBlank()) {
            run.putDetail("traceId", entity.getTraceId());
        }
        return run;
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.toInstant(ZoneOffset.UTC);
    }
}
