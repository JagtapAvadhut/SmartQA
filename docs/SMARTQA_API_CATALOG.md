# API catalog

Envelope (most JSON APIs): `ApiResponse<T>` = `{ success, message, data, errorCode, timestamp }`.

Health endpoints return their own records (not always wrapped).

Base URL in UI: `/api` proxied to `http://localhost:8081`.

## Health and settings

| Method | Path | Behavior |
|--------|--------|----------|
| GET | `/api/health` | DB probe (`projects` count, 2s). `{ status: UP\|DOWN, name: SmartQA }` |
| GET | `/api/health/generation` | **Static** `{ status: UP, name: SmartQA, browserReady: true, browser: Chromium, engine: Playwright Java }`. Does **not** launch a browser. |
| GET | `/api/health/ai` | `FallbackAiProvider.healthAll()` wrapped in `ApiResponse` |
| GET | `/api/settings/ai` | Non-secret AI settings (`AiSettingsResponse`: provider names, models, `geminiConfigured`, timeout) |

## Workspace and pipeline

| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | `/api/workspace/analyze` | `WorkspaceAnalyzeRequest` (`applicationUrl` required, `instructions`, ids, `structuredSteps`) | `WorkspaceAnalyzeResponse` |
| POST | `/api/workspace/generate-and-validate` | `PipelineStartRequest` | `PipelineRunResponse` (started) |
| GET | `/api/pipelines/{id}` | | `PipelineRunResponse` |
| GET | `/api/test-cases/{testCaseId}/pipelines/latest` | | latest or `data: null` |
| POST | `/api/pipelines/{id}/stop` | | stop requested |
| POST | `/api/pipelines/{id}/fix-and-rebuild` | | `SourceFixProposal` or 404 if none |
| GET | `/api/pipelines/{id}/stream` | SSE `TEXT_EVENT_STREAM`, `lastEventId` | `ProgressEvent` |

Analyze timeout is derived from intent timeout (bounded 30–120s). Timeout → `AI_TIMEOUT`.

## Projects and test cases

| Method | Path | Notes |
|--------|------|--------|
| GET/POST | `/api/projects` | POST `ProjectRequest` (`name`, `description`, `applicationUrl`, `environment`) → **201** |
| GET/PUT/DELETE | `/api/projects/{id}` | |
| GET/POST | `/api/projects/{projectId}/test-cases` | POST `TestCaseRequest` (`name`, `description`, `naturalLanguage`) → **201** |
| GET/PUT/DELETE | `/api/test-cases/{id}` | |

## Intent

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/test-cases/{id}/understand` | Compile/analyze intent |
| POST | `/api/test-cases/{id}/clarify` | `ClarifyRequest.answers` |
| POST | `/api/test-cases/{id}/accept` | Accept contract |

## Generation

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/test-cases/{id}/generate` | Starts async generate; returns existing run if already running |
| GET | `/api/generation-runs/{runId}` | **In-memory registry only** |
| GET | `/api/test-cases/{id}/generation/latest` | |
| PUT | `/api/test-cases/{id}/code` | `GeneratedCodeRequest.generatedCode`; quality gate required |
| GET | `/api/test-cases/{id}/generation/stream` | SSE, `lastEventId` |

## Execution

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/test-cases/{id}/execute` | Optional `ExecutionStartRequest` (`executionProvider`, `browserMode`, `headless`) |
| GET | `/api/execution-runs/{id}` | |
| POST | `/api/execution-runs/{id}/stop` | **409/error** if status is not `RUNNING` |
| GET | `/api/execution-runs/{id}/stream` | SSE |
| GET | `/api/execution-runs/{id}/events` | In-memory `ExecutionEventStore` |
| GET | `/api/execution-runs/{id}/events/{stepNumber}` | |

## Validation

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/test-cases/{id}/validate` | Independent generated-test run |
| GET | `/api/test-cases/{id}/validations` | History |
| GET | `/api/validation-runs/{id}` | |
| GET | `/api/validation-runs/{id}/stream` | SSE |

## Screenshots, clarifications, debug, RAG

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/execution-runs/{runId}/screenshots` | Metadata list |
| GET | `/api/screenshots/{screenshotId}` | **raw PNG** (not `ApiResponse`); 404 if missing |
| GET | `/api/runtime-clarifications/{id}` | |
| POST | `/api/runtime-clarifications/{id}/resolve` | `{ selectedCandidateId, selectedOption }` |
| GET | `/api/debug/traces/{traceId}` | Trace events |
| GET | `/api/debug/traces/{traceId}/download` | `format=log\|jsonl` attachment |
| POST | `/api/debug/traces/{traceId}/events` | UI ingest (capped) |
| GET | `/api/internal/rag/stats` | Internal RAG/pgvector stats (not wrapped) |

## Errors (`GlobalExceptionHandler`)

| Kind | HTTP |
|------|------|
| `ResourceNotFoundException` | 404 |
| `ConflictException` / `ClarificationRequiredException` | 409 (`WAITING_FOR_CLARIFICATION` payload) |
| Validation / intent | 400 |
| Quality gate / many locator/state failures | 422 |
| AI provider | 502 |
| AI timeout / execution timeout | 504 |
| AI unavailable | 503 |
| AI rate limited | 429 |

Secrets in traces are masked by `SecretMasker` (`***MASKED***`).
