package com.smartqa.pipeline;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("pipeline_runs")
public class PipelineRunEntity {

    @Id
    private UUID id;
    private String status;
    private String stage;
    @Column("user_stage_label")
    private String userStageLabel;
    @Column("project_id")
    private UUID projectId;
    @Column("test_case_id")
    private UUID testCaseId;
    @Column("generation_run_id")
    private UUID generationRunId;
    @Column("validation_run_id")
    private UUID validationRunId;
    @Column("execution_run_id")
    private UUID executionRunId;
    @Column("application_url")
    private String applicationUrl;
    @Column("trace_id")
    private String traceId;
    @Column("started_at")
    private LocalDateTime startedAt;
    @Column("finished_at")
    private LocalDateTime finishedAt;
    @Column("duration_ms")
    private Long durationMs;
    @Column("error_message")
    private String errorMessage;
    @Column("final_summary")
    private String finalSummary;
    private int attempt;
    @Column("max_attempts")
    private int maxAttempts;
    @Column("details_json")
    private String detailsJson;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getUserStageLabel() { return userStageLabel; }
    public void setUserStageLabel(String userStageLabel) { this.userStageLabel = userStageLabel; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getTestCaseId() { return testCaseId; }
    public void setTestCaseId(UUID testCaseId) { this.testCaseId = testCaseId; }
    public UUID getGenerationRunId() { return generationRunId; }
    public void setGenerationRunId(UUID generationRunId) { this.generationRunId = generationRunId; }
    public UUID getValidationRunId() { return validationRunId; }
    public void setValidationRunId(UUID validationRunId) { this.validationRunId = validationRunId; }
    public UUID getExecutionRunId() { return executionRunId; }
    public void setExecutionRunId(UUID executionRunId) { this.executionRunId = executionRunId; }
    public String getApplicationUrl() { return applicationUrl; }
    public void setApplicationUrl(String applicationUrl) { this.applicationUrl = applicationUrl; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getFinalSummary() { return finalSummary; }
    public void setFinalSummary(String finalSummary) { this.finalSummary = finalSummary; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
