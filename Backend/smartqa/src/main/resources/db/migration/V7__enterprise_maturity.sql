CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    project_id UUID,
    test_case_id UUID,
    actor VARCHAR(200),
    action VARCHAR(80) NOT NULL,
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_project ON audit_events (project_id, created_at DESC);

CREATE TABLE IF NOT EXISTS jira_connections (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    base_url VARCHAR(500) NOT NULL,
    email VARCHAR(200),
    api_token_encrypted TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_jira_connections_project ON jira_connections (project_id);

CREATE TABLE IF NOT EXISTS jira_imports (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    test_case_id UUID REFERENCES test_cases(id) ON DELETE SET NULL,
    issue_key VARCHAR(80) NOT NULL,
    summary TEXT,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS scheduled_runs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    test_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    cron_expr VARCHAR(80),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_run_at TIMESTAMP,
    last_status VARCHAR(40),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_scheduled_runs_project ON scheduled_runs (project_id, enabled);

CREATE TABLE IF NOT EXISTS runtime_clarifications (
    id UUID PRIMARY KEY,
    test_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    generation_run_id UUID,
    step_id VARCHAR(80),
    question TEXT NOT NULL,
    options_json TEXT,
    selected_option TEXT,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP
);
