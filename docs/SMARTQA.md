# SmartQA

SmartQA is a natural-language browser test platform. A tester supplies an application URL and instructions. The backend compiles intent, drives Chromium with Playwright Java, records locators, generates a JUnit/Playwright Java test, statically quality-gates that code, then optionally validates and executes it in a real browser.

This document matches the **current workspace source** under `Backend/smartqa` and `UI/smartqa_ui`.

## What exists

| Layer | Implementation |
|-------|----------------|
| API | Spring Boot WebFlux (`com.smartqa`), port **8081** |
| UI | React 19 + Vite (`UI/smartqa_ui`), port **5300**, proxy `/api` → 8081 |
| Browser executor | `PlaywrightBrowserExecutionProvider` — **the only working executor** |
| Optional MCP class | `PlaywrightMcpBrowserExecutionProvider` — **does not execute** (throws even if MCP is reachable) |
| AI | `FallbackAiProvider` → `GeminiAiProvider` + `OllamaAiProvider` |
| Persistence | PostgreSQL + Flyway V1–V7, R2DBC |
| Vectors | `pgvector` table `smartqa_knowledge` |
| Generated tests | Playwright Java stored on `test_cases.generated_code` |

## Honest constraints

- SmartQA does **not** claim 100% accuracy.
- SmartQA does **not** claim every website works.
- SmartQA is **not production-ready** until a real-browser acceptance matrix is green. That matrix is env-gated and is not the default `mvn test` suite.
- `GET /api/health/generation` returns a **static** JSON payload (`UP`, Chromium, Playwright Java). It does **not** launch a browser.

## High-level flow (as coded)

```
UI Workspace (OverviewPage)
  → POST /api/workspace/generate-and-validate
  → PipelineService
      PREFLIGHT → UNDERSTAND → PLAN → GENERATE → QUALITY_GATE
      → VALIDATE (GeneratedTestValidator + IsolatedTestExecutor)
      → EXECUTE (ExecutionService → PlaywrightBrowserExecutionProvider)
      → DIAGNOSE / RECOVER (bounded)
      → COMPLETE
```

Playwright is the executor on both the generation browser session and the generated-test validator. Gemini/Ollama **reason**. `TargetSafetyGate` **checks** an AI hypothesis against live candidates. Playwright **acts**.

## Packages (selected)

| Package | Role |
|---------|------|
| `com.smartqa.pipeline` | One-click generate & validate |
| `com.smartqa.workspace` | Analyze URL + instructions into a project/test case |
| `com.smartqa.intent` | Intent contract, DAG, clarifications |
| `com.smartqa.generation` | Live browser generation + Playwright Java codegen |
| `com.smartqa.validation` | Independent generated-test validator |
| `com.smartqa.execution` | Re-run generated (or live) tests |
| `com.smartqa.browser` | Playwright provider, filters, search, healing |
| `com.smartqa.browser.intelligence` | DOM, tree, ranker, constraints, memory |
| `com.smartqa.browser.intelligence.cdp` | CDP/AX evidence (not execution) |
| `com.smartqa.browser.multimodal` | Graph, visual regions, AI target hypothesis, safety gate |
| `com.smartqa.ai` | Gemini key pool, Ollama fallback |
| `com.smartqa.rag` | pgvector retrieval (advisory) |
| `com.smartqa.security` | Prompt-injection wrapping of untrusted evidence |

## Related docs

| File | Topic |
|-------|--------|
| [SMARTQA_CURRENT_STATUS.md](SMARTQA_CURRENT_STATUS.md) | Implemented / tested / blocked |
| [SMARTQA_CURRENT_ARCHITECTURE.md](SMARTQA_CURRENT_ARCHITECTURE.md) | End-to-end architecture |
| [SMARTQA_BROWSER_INTELLIGENCE.md](SMARTQA_BROWSER_INTELLIGENCE.md) | DOM, tree, graph, physical control |
| [SMARTQA_CDP.md](SMARTQA_CDP.md) | CDP / accessibility |
| [SMARTQA_AI.md](SMARTQA_AI.md) | Gemini, key pool, Ollama |
| [SMARTQA_RAG.md](SMARTQA_RAG.md) | pgvector RAG |
| [SMARTQA_API_CATALOG.md](SMARTQA_API_CATALOG.md) | HTTP + SSE |
| [SMARTQA_DATABASE.md](SMARTQA_DATABASE.md) | Flyway schema |
| [SMARTQA_UI.md](SMARTQA_UI.md) | UI routes and controls |
| [SMARTQA_RUNTIME_FLOW.md](SMARTQA_RUNTIME_FLOW.md) | Stage-by-stage path |
| [SMARTQA_TEAM_ORCHESTRATION.md](SMARTQA_TEAM_ORCHESTRATION.md) | Referenced by `PipelineService` |
