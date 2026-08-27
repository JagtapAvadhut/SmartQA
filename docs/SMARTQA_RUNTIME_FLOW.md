# Runtime flow

## One-click Generate & Validate

`POST /api/workspace/generate-and-validate` (`PipelineStartRequest`)

Body fields used by code: `applicationUrl`, `instructions`, `projectId`, `testCaseId`, `structuredSteps`, `browserMode`, `headless`, `skipExecution`, `maxAttempts` (clamped 1–5, default 3).

`PipelineService.start`:

1. Create `PipelineRun` (`QUEUED` → `RUNNING`), register in `PipelineRunRegistry`, persist via `PipelineRunPersistenceService`.
2. **PREFLIGHT**
   - Postgres: `projectRepository.count()` (3s). Failure → cannot start.
   - AI: `FallbackAiProvider.healthAll()` unless `structuredSteps` is non-empty (`skipAiPreflight`). If no provider is usable → `AI_UNAVAILABLE`.
   - Embeddings: non-fatal warning `EMBEDDING_UNAVAILABLE`.
3. **UNDERSTAND** — `WorkspaceService.analyze` creates/updates project + test case, compiles intent. If contract status is `NEEDS_CLARIFICATION` → pipeline `BLOCKED`.
4. **GENERATE** — `GenerationService.generate` on a blocking scheduler:
   - Live Playwright session (`PlaywrightBrowserExecutionProvider`).
   - Locator memory persisted on the test case.
   - Prefer `DeterministicPlaywrightFactory` Java; Gemini codegen only if the deterministic source fails `QualityGateService`.
   - Quality gate compile is **not** validation-passed.
5. **VALIDATE** — `GeneratedTestValidator.validate` runs stored Java in isolation.
6. **EXECUTE** — `ExecutionService.execute` unless `skipExecution` (then `VALIDATED_NOT_EXECUTED`).
7. On failure: diagnose → optional safe recovery / source-fix proposal → retry until `maxAttempts`.
8. Stop: `POST /api/pipelines/{id}/stop` sets stop flag and cancels via `ExecutionCancellationRegistry`.

## SSE

| Stream | Channel |
|--------|---------|
| `GET /api/pipelines/{id}/stream` | `ProgressEventHub.pipelineChannel(id)` |
| `GET /api/test-cases/{id}/generation/stream` | generation channel (test case id) |
| `GET /api/execution-runs/{id}/stream` | execution channel |
| `GET /api/validation-runs/{id}/stream` | validation channel |

Query resume token: `lastEventId` (pipeline) or `lastEventId` (generation/execution/validation). Heartbeat comment `ping` every 15s.

UI `sse.ts` reconnects (max 5, backoff 1s–8s) and recovers terminal state via GET of the run.

## Status lifecycle

**Pipeline:** `QUEUED` → `RUNNING` → `PASS` | `VALIDATED_NOT_EXECUTED` | `FAIL` | `BLOCKED` | `STOPPED` | `ABANDONED` (in-flight row after process restart; no durable heartbeat).

**Generation run (in-memory only):** `RUNNING` → `COMPLETED` | `FAILED` | `STOPPED` | `WAITING_FOR_CLARIFICATION`.

**Test case:** `DRAFT`, `READY`, `RUNNING`, `PASSED`, `FAILED`, `ERROR`, `ANALYSIS_FAILED`.

**Execution run:** persisted on `execution_runs` (`status`, timestamps, exit_code, stdout/stderr).

**Validation run:** `validation_runs.status` default `PENDING`.
