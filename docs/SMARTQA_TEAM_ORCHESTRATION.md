# Team orchestration (pipeline)

Referenced by `PipelineService`. This is the **coded** orchestration, not a staffing plan.

## Agents as software stages

| Stage | Owner class | Browser? |
|-------|-------------|---------|
| Preflight | `PipelineService.preflight` | No |
| Understand | `WorkspaceService` + `IntentService` | No |
| Generate | `GenerationService` + `PlaywrightBrowserExecutionProvider` | Yes |
| Quality gate | `QualityGateService` | No |
| Validate | `GeneratedTestValidator` | Yes (isolated JUnit) |
| Execute | `ExecutionService` | Yes |
| Diagnose | `FailureDiagnostician`, `ConsensusResolver` | No |
| Recover | `FailureAwareRecoveryService`, `SafeRecoveryExecutor` | Maybe (bounded Playwright retries) |
| Source fix proposal | `DevelopmentFixLoopService` / `SourceFixProposal` | No (proposal only) |

## Rules

- One pipeline id correlates generation, validation, execution, SSE (`RunCorrelation`).
- User-facing labels (`userStageLabel`) are not raw enum names.
- `VALIDATED_NOT_EXECUTED` is not `PASS`.
- Stop is cooperative via `ExecutionCancellationRegistry`.
- AI consensus does not execute recovery plans until `RecoveryPlanValidator` accepts them.
