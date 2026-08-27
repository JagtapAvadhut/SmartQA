CREATE TABLE IF NOT EXISTS pipeline_runs (
    id                   UUID PRIMARY KEY,
    status               VARCHAR(48) NOT NULL,
    stage                VARCHAR(64),
    user_stage_label     VARCHAR(128),
    project_id           UUID,
    test_case_id         UUID,
    generation_run_id    UUID,
    validation_run_id    UUID,
    execution_run_id     UUID,
    application_url      TEXT,
    trace_id             VARCHAR(64),
    started_at           TIMESTAMP,
    finished_at          TIMESTAMP,
    duration_ms          BIGINT,
    error_message        TEXT,
    final_summary        TEXT,
    attempt              INTEGER NOT NULL DEFAULT 1,
    max_attempts         INTEGER NOT NULL DEFAULT 3,
    details_json         TEXT,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_test_case_id ON pipeline_runs(test_case_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_status ON pipeline_runs(status);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_started_at ON pipeline_runs(started_at DESC);
