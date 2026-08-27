CREATE TABLE IF NOT EXISTS validation_runs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id   UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    result         TEXT,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    started_at     TIMESTAMP,
    finished_at    TIMESTAMP,
    duration_ms    BIGINT,
    stdout         TEXT,
    stderr         TEXT,
    error_message  TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_validation_runs_test_case_id ON validation_runs(test_case_id);
