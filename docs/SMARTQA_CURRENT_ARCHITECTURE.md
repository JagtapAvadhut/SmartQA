# SmartQA current architecture

Source of truth: `Backend/smartqa/src/main/java/com/smartqa`.

## Roles

| Role | Actual class | Executes browser? |
|------|----------------|-------------------|
| Intent | `IntentService`, `InstructionIntentCompiler` | No |
| Live generation session | `GenerationService` + `PlaywrightBrowserExecutionProvider` | **Yes** (Playwright) |
| Browser evidence | `BrowserIntelligenceService`, `DomExtractor` | No |
| Hierarchy | `ElementTree` | No |
| Typed relationships | `CandidateRelationshipGraph` | No |
| Normalized control | `PhysicalControl` | No |
| CDP / AX | `CdpBrowserIntelligence` | No |
| Ranking | `LocatorRanker`, `HardConstraintChecker` | No |
| Resolution | `ElementResolver` | Uses Playwright locators after ranking |
| Actionability | `ActionabilityVerifier` | Checks, then Playwright acts |
| AI hypothesis | `MultimodalTargetDiscoveryEngine` + Gemini/Ollama | No |
| Safety | `TargetSafetyGate` | No — rejects unsafe hypotheses |
| Executor | `PlaywrightBrowserExecutionProvider` | **Yes** |
| Static quality | `QualityGateService` | No (parse + `javax.tools` compile) |
| Independent validator | `GeneratedTestValidator` + `IsolatedTestExecutor` | **Yes** (generated JUnit) |
| Memory | `ExecutionMemoryService` (in-memory, advisory) | No |
| RAG | `RagRetrievalService` | No |

## Tree vs graph vs physical control vs CDP vs Playwright

```
Playwright Page  ──extract──►  ElementCandidate[]   (DomExtractor)
        │
        ├── ElementTree                 hierarchy (parent/child/depth)
        ├── CandidateRelationshipGraph    typed edges (structural, heading, …)
        ├── PhysicalControl             normalized control projection
        ├── CdpBrowserIntelligence        optional Chromium CDP + AX tree
        └── LocatorRanker / ElementResolver
                │
                ▼
        TargetSafetyGate (if AI proposed a candidate)
                │
                ▼
        Playwright locator click/fill/select/assert
```

- **DOM** is the live Playwright-derived candidate inventory.
- **Tree** is a hierarchical view of that inventory (`ElementTree`). It is not a second store.
- **Graph** is typed relationships among the same candidates (`CandidateRelationshipGraph`).
- **PhysicalControl** is a normalized “this is a checkbox / combobox / …” projection. Evidence only — not an executor.
- **CDP / AX** is extra Chromium evidence. Capture failure is non-fatal (`CdpCapture.unavailable(...)`).
- **Playwright** is the only implemented executor.

## Pipeline stages (`PipelineStage`)

`PREFLIGHT` → `UNDERSTAND` → `PLAN` → `GENERATE` → `QUALITY_GATE` → `VALIDATE` → `EXECUTE` → `DIAGNOSE` / `RECOVER` → `COMPLETE`

Pipeline statuses (`PipelineRun`): `QUEUED`, `RUNNING`, `PASS`, `VALIDATED_NOT_EXECUTED`, `FAIL`, `BLOCKED`, `STOPPED`, `ABANDONED`.

`VALIDATED_NOT_EXECUTED` means the generated-test validator passed and final execution was skipped. It is **not** a full `PASS`.

## AI vs gate vs executor

1. Deterministic ranking tries first (`LocatorRanker`, ownership, hard constraints).
2. If confidence is below `smartqa.intelligence.ai-escalation-threshold` (default **0.70**), AI may propose a live candidate id.
3. `TargetSafetyGate` verifies visibility, enabled, ownership, compatibility, and live-set membership.
4. Only then does Playwright perform the action.

AI never owns `Page.click`. MCP never owns it in v1 (`PlaywrightMcpBrowserExecutionProvider` throws and tells the operator to use `smartqa.browser.provider=playwright`).

## Quality gate vs independent validator

| | Quality gate | Independent validator |
|--|----------------|------------------------|
| Class | `QualityGateService` | `GeneratedTestValidator` |
| Input | Generated Java source | Stored `generated_code` |
| Checks | Playwright/JUnit imports, `@Test`, forbidden APIs, compile | Compiles and **runs** the test via `IsolatedTestExecutor` |
| Browser | No | **Yes** |
| Can declare `VALIDATION_PASSED` | No | **Yes** (only this path) |

## Persistence split

- **Durable:** projects, test cases, intent reviews, execution_runs, validation_runs, pipeline_runs, smartqa_knowledge, audit/jira/schedule/clarification tables.
- **In-memory only:** `GenerationRun` / `GenerationRunRegistry`. `pipeline_runs.generation_run_id` is a UUID with **no** `generation_runs` table.

## MCP

`smartqa.mcp.enabled` defaults to **false**. `BrowserExecutionRouter` rejects `mcp` while disabled. The MCP provider class exists for a future wire-up; it is **not** a working executor in this source.
