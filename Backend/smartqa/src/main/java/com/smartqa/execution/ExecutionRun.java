package com.smartqa.execution;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("execution_runs")
public class ExecutionRun {

    @Id
    private UUID id;
    @Column("test_case_id")
    private UUID testCaseId;
    private String status;
    @Column("started_at")
    private LocalDateTime startedAt;
    @Column("finished_at")
    private LocalDateTime finishedAt;
    @Column("duration_ms")
    private Long durationMs;
    @Column("exit_code")
    private Integer exitCode;
    private String stdout;
    private String stderr;
    @Column("error_message")
    private String errorMessage;
    @Column("scenario_results")
    private String scenarioResults;
    @Column("healing_events")
    private String healingEvents;
    @Column("created_at")
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(UUID testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getScenarioResults() {
        return scenarioResults;
    }

    public void setScenarioResults(String scenarioResults) {
        this.scenarioResults = scenarioResults;
    }

    public String getHealingEvents() {
        return healingEvents;
    }

    public void setHealingEvents(String healingEvents) {
        this.healingEvents = healingEvents;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
