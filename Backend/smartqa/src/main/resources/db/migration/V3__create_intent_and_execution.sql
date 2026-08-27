CREATE TABLE intent_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    contract_json TEXT,
    clarifications_json TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_intent_reviews_case ON intent_reviews (test_case_id, created_at DESC);

CREATE TABLE execution_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms BIGINT,
    exit_code INT,
    stdout TEXT,
    stderr TEXT,
    error_message TEXT,
    scenario_results TEXT,
    healing_events TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_execution_runs_case ON execution_runs (test_case_id, created_at DESC);
