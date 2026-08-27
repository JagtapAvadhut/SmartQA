# UI architecture

App: `UI/smartqa_ui` (React 19, Vite 8, Tailwind 4, react-router 7).

## Routes (`App.tsx`)

| Path | Page | Role |
|------|------|------|
| `/` | `OverviewPage` | **Workspace** — URL + instructions, Generate & Validate |
| `/tests` | `TestsPage` | Test list, Open, **Run again** |
| `/projects` | `ProjectsPage` | Projects |
| `/projects/:projectId` | `ProjectDetailPage` | Project detail |
| `/projects/:projectId/test-cases/:testCaseId` | `TestCasePage` | Single test: generate/execute/stop |
| `/settings` | `SettingsPage` | Read-only AI settings from API |

Nav (`Layout.tsx`): Workspace, Tests, Projects, Settings. Sidebar shows backend health plus Gemini/Ollama usable flags (`healthyKeys/configuredKeys` for Gemini). **Retry connection** when API is down.

## Workspace controls (`OverviewPage`)

Implemented labels (source):

- **Generate & Validate** — `POST /api/workspace/generate-and-validate`
- **New test** — clears draft/session
- **Refresh** — `recoverDurableState()`
- **Load example** — Automall example text
- **Stop Test** — `POST /api/pipelines/{id}/stop` while pipeline running
- Advanced: Analyze, Generate, Validate, Execute separately
- **Advanced Steps** — `StepBuilder` structured steps (location is a search hint)
- Headed / headless select
- Recent tests
- `ClarificationModal` for runtime clarifications
- `PipelineTimeline`, `ScreenshotTimeline`, `CurrentAction`, `StepTimeline`
- `ValidationHistory`
- `DebugTracePanel` (technical trace)
- AI diagnosis via pipeline `diagnosis` / humanized copy

There is no separate route named “Workspace”; `/` is the workspace.

## SSE

`src/api/sse.ts` uses `EventSource`, named events (pipeline, generation, execution, validation, CDP, MCP, quality gate, …), last-event resume, reconnect, and GET fallback for terminal status.

## API modules

`health.ts`, `settings.ts`, `projects.ts`, `testcases.ts`, `workflow.ts`, `pipeline.ts`, `sse.ts`, `screenshots.ts`, `runtimeClarifications.ts`, `debug.ts`, `client.ts` (envelope unwrap, secret masking).

## Build

- Dev: Vite port **5300**, `strictPort`, proxy `/api` → `http://localhost:8081` (300s timeout).
- `npm run build` = `tsc -b && vite build`.
- No UI unit-test script. Backend `WorkspaceUiSmokeTest` is env-gated (`SMARTQA_UI_SMOKE=true`).
