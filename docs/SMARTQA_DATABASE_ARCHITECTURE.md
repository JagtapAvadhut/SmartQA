# Database architecture

Flyway locations: `Backend/smartqa/src/main/resources/db/migration`.  
Runtime: PostgreSQL via R2DBC; Flyway uses JDBC. Default DB name `smartqa`.

There is **no** `generation_runs` table. Generation runs live in `GenerationRunRegistry` (memory). `pipeline_runs.generation_run_id` is an unscoped UUID.

## V1 `projects`

`id` UUID PK, `name`, `description`, `application_url`, `environment`, `created_at`, `updated_at`.  
Index: `idx_projects_updated_at`.

## V2 `test_cases` / scenarios / steps

`test_cases`: `id`, `project_id` FK cascade, `name`, `description`, `status`, `natural_language`, `generated_code`, `locator_memory`, `intent_contract`, timestamps.

`test_scenarios`: `test_case_id` FK, `scenario_name`, `scenario_order`.

`test_steps`: `scenario_id` FK, `step_order`, `step_text`.

## V3 intent + execution

`intent_reviews`: `test_case_id`, `status`, `contract_json`, `clarifications_json`, `created_at`.

`execution_runs`: `test_case_id`, `status`, `started_at`, `finished_at`, `duration_ms`, `exit_code`, `stdout`, `stderr`, `error_message`, `scenario_results`, `healing_events`, `created_at`.

## V4 `validation_runs`

`test_case_id`, `status` default `PENDING`, `result`, `attempt_number`, timestamps, `duration_ms`, `stdout`, `stderr`, `error_message`.

## V5 `pipeline_runs`

`id` UUID PK (no DB default — application sets id), `status`, `stage`, `user_stage_label`, `project_id`, `test_case_id`, `generation_run_id`, `validation_run_id`, `execution_run_id`, `application_url`, `trace_id`, timestamps, `duration_ms`, `error_message`, `final_summary`, `attempt`, `max_attempts`, `details_json`.

No FKs on the run id columns. Indexes on `test_case_id`, `status`, `started_at`.

## V6 RAG `smartqa_knowledge`

Requires `CREATE EXTENSION vector`.

Columns: `id`, `scope` (`GLOBAL_GENERIC` \| `APPLICATION` \| `EXECUTION`), `scope_key`, `content`, `content_type`, `source`, `source_run_id`, `source_test_case_id`, `metadata_json`, `confidence`, `success_count`, `failure_count`, `last_used_at`, `embedding vector(768)`, timestamps.

HNSW index: `idx_smartqa_knowledge_embedding_hnsw` (`vector_cosine_ops`).

## V7 enterprise tables

`audit_events`, `jira_connections` (encrypted token column), `jira_imports`, `scheduled_runs` (`cron_expr`, `enabled` default false), `runtime_clarifications`.

Jira import and schedule services exist; `smartqa.jira.enabled` and `smartqa.schedule.enabled` default **false**.

## Ownership

Project scope is `projects.id`. Test cases, runs, Jira, and schedules hang off `project_id` / `test_case_id`. `ProjectAccessGuard` enforces project access in code. There is no multi-tenant user table in these migrations.
