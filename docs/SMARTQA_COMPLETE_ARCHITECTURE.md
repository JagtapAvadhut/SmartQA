# SmartQA complete architecture (as implemented)

Verified against `Backend/smartqa` and `UI/smartqa_ui`. Conceptual names from older design notes are mapped to **actual class names**.

## End-to-end path

```
User (Workspace OverviewPage)
  URL + natural-language (or structured steps)
    → POST /api/workspace/analyze          WorkspaceController / WorkspaceService
    → POST /api/workspace/generate-and-validate
         PipelineController / PipelineService
            PREFLIGHT   DB + AI health (AI skippable when structured steps exist)
            UNDERSTAND  IntentService / InstructionIntentCompiler
            PLAN        intent contract + execution plan DAG
            GENERATE    GenerationService
                          PlaywrightBrowserExecutionProvider (live Chromium)
                          BrowserIntelligenceService
                            DomExtractor
                            CdpBrowserIntelligence (evidence)
                            ElementTree (hierarchy)
                            CandidateRelationshipGraph (typed relations)
                            PhysicalControl (normalized control)
                            Context / ownership
                            LocatorRanker + HardConstraintChecker
                            ElementResolver
                            ActionabilityVerifier
                            FilterEngine / SearchIntelligence when relevant
                            MultimodalTargetDiscoveryEngine (optional)
                            TargetSafetyGate (AI hypothesis vs live set)
                            Playwright action
                            StateTransitionVerifier / assertions
                          ExecutionMemoryService (in-memory, advisory)
                          RagRetrievalService (advisory snippets)
                          Playwright Java source
            QUALITY_GATE QualityGateService (static + javac, no browser)
            VALIDATE    GeneratedTestValidator + IsolatedTestExecutor
                        (only this path may emit VALIDATION_PASSED)
            EXECUTE     ExecutionService (Playwright again unless skipExecution)
            DIAGNOSE    FailureDiagnostician / ConsensusResolver
            RECOVER     FailureAwareRecoveryService / PageRecoveryPlanner
            COMPLETE    pipeline_runs row
    → UI: SSE stream, screenshots, diagnosis, technical trace
```

## Tree vs graph vs physical control

| Concept | Class | Meaning |
|---------|--------|---------|
| DOM inventory | `DomExtractor` → `ElementCandidate` | Live Playwright-derived nodes |
| Tree | `ElementTree` | Hierarchy (parent, children, depth) |
| Graph | `CandidateRelationshipGraph` | Typed relationships among the same candidates |
| Physical control | `PhysicalControl` | Normalized control (checkbox, combobox, …). Evidence, not an executor |
| CDP / AX | `CdpBrowserIntelligence` | Chromium evidence; failure is non-fatal |
| Layout | bounding boxes on candidates / CDP rects | Geometry for ranking and visual overlap |
| Executor | `PlaywrightBrowserExecutionProvider` | The only working click/fill/navigate path |

`TreeGraphReconciler` flags inconsistent tree/graph evidence; `TargetSafetyGate` can reject unresolved tree/graph.

## AI vs safety vs Playwright

- **AI reasons** (`GeminiAiProvider` / `OllamaAiProvider` via `FallbackAiProvider`).
- **`TargetSafetyGate` verifies** the hypothesis against the **current** ranked live candidates (visible, enabled, ownership, compatibility).
- **Playwright executes**.

MCP (`PlaywrightMcpBrowserExecutionProvider`) is compiled, default-disabled, and **throws even when the MCP URL is reachable**, instructing the operator to keep `smartqa.browser.provider=playwright`.

## Quality gate vs independent validator

| | Quality gate | Independent validator |
|--|----------------|------------------------|
| Class | `QualityGateService` | `GeneratedTestValidator` |
| Browser | No | Yes (`IsolatedTestExecutor`) |
| May say VALIDATION_PASSED | No | **Yes** |

## RAG

`RagRetrievalService` + `smartqa_knowledge` (pgvector, cosine, 768-d). Advisory. Live DOM wins. Missing embeddings do not fail the pipeline (event `EMBEDDING_UNAVAILABLE`).
