CREATE TABLE test_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(300) NOT NULL,
    description TEXT,
    status VARCHAR(40) NOT NULL,
    natural_language TEXT,
    generated_code TEXT,
    locator_memory TEXT,
    intent_contract TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_test_cases_project ON test_cases (project_id, updated_at DESC);

CREATE TABLE test_scenarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    scenario_name VARCHAR(300) NOT NULL,
    scenario_order INT NOT NULL
);

CREATE INDEX idx_test_scenarios_case ON test_scenarios (test_case_id, scenario_order);

CREATE TABLE test_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_id UUID NOT NULL REFERENCES test_scenarios(id) ON DELETE CASCADE,
    step_order INT NOT NULL,
    step_text TEXT NOT NULL
);

CREATE INDEX idx_test_steps_scenario ON test_steps (scenario_id, step_order);
