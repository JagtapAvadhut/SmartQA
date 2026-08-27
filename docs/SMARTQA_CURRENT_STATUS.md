# SmartQA current status

Audit date: 2026-08-27. Source: this workspace. **Not production-ready.**

## Product status: NOT PRODUCTION-READY

Reasons (evidence-based):

1. Default `mvn test` does **not** run the live-site matrix (Flipkart, Urban Company, OrangeHRM, etc.). Those classes are `@EnabledIfEnvironmentVariable`.
2. Playwright MCP is **not** an executor: `PlaywrightMcpBrowserExecutionProvider` throws even when the MCP URL answers, and `smartqa.mcp.enabled` defaults to `false`.
3. `GET /api/health/generation` is a **static** JSON body. It does not launch Chromium.
4. Generation run state is **in-memory** (`GenerationRunRegistry`). Process restart abandons in-flight generation; pipeline rows may become `ABANDONED`.
5. Schedule and Jira tables exist; `smartqa.schedule.enabled` and `smartqa.jira.enabled` default **false**.
6. GitHub `JagtapAvadhut/SmartQA` historically held an unrelated `spring/` + `react/` starter. This workspace is the product; the remote is replaced only after this sync.

Do **not** document 100% accuracy, “all websites”, or production-candidate.

## Implemented (code exists)

| Area | Status |
|------|--------|
| Workspace URL + NL → pipeline | Implemented (`PipelineService`, `POST /api/workspace/generate-and-validate`) |
| Intent compile / clarify / accept | Implemented |
| Playwright Java executor | Implemented (`PlaywrightBrowserExecutionProvider`) — **only working executor** |
| DOM / tree / graph / physical control | Implemented (`DomExtractor`, `ElementTree`, `CandidateRelationshipGraph`, `PhysicalControl`) |
| CDP + AX | Implemented, default **on**, mode `ESCALATE`; capture failure is non-fatal |
| Filters / search | Implemented (`FilterEngine`, `SearchIntelligence`) |
| Gemini key pool + 429/5xx/timeout/auth cooldown | Implemented (`GeminiKeyPool`) |
| Ollama fallback | Implemented (`FallbackAiProvider`) |
| Target safety gate | Implemented (`TargetSafetyGate`) — AI does not click |
| Quality gate | Implemented (`QualityGateService`) — static + compile, **no browser** |
| Independent validator | Implemented (`GeneratedTestValidator` + `IsolatedTestExecutor`) |
| RAG pgvector | Implemented (`smartqa_knowledge`, 768-d, cosine, threshold 0.55) — advisory |
| SSE + screenshot PNG | Implemented |
| UI workspace / tests / projects / settings | Implemented |
| Prompt injection wrap + secret mask | Implemented (`PromptInjectionGuard`, `SecretMasker`) |

## Configured defaults (`application.yml`)

| Flag | Default |
|------|---------|
| `smartqa.mcp.enabled` | **false** |
| `smartqa.browser.provider` | `playwright` |
| `smartqa.browser.headless` | **false** (headed) |
| `smartqa.intelligence.cdp-enabled` | **true** |
| `smartqa.rag.enabled` | **true** (needs Postgres + embeddings at runtime) |
| `smartqa.schedule.enabled` | **false** |
| `smartqa.jira.enabled` | **false** |
| AI primary | `gemini` (`gemini-flash-latest`) |
| AI fallback | `ollama` (`qwen2.5-coder:3b`) |

## Tests (this session)

Test **classes** on disk: **147** `*Test.java` files.

Env-gated (skipped unless flag is `true`):

| Flag | Class |
|------|--------|
| `SMARTQA_REAL_MATRIX` | `RealWorldMatrixRegressionTest` |
| `SMARTQA_RAG_IT` | `RagPgVectorIntegrationTest` |
| `SMARTQA_SEARCH_REAL` | `SearchRealRegressionTest` |
| `SMARTQA_URBANCOMPANY_REAL` | `UrbanCompanyRuntimeRealRegressionTest` |
| `SMARTQA_ORANGEHRM_REAL` | `OrangeHrMRealRegressionTest` |
| `SMARTQA_UI_SMOKE` | `WorkspaceUiSmokeTest` |

Maven/UI counts from **this session** (2026-08-27):

| Check | Result |
|-------|--------|
| `mvn test` | **BUILD FAILURE** — Tests run: **514**, Failures: **4**, Errors: **1**, Skipped: **8** (501 passed) |
| `npm run build` | **PASS** (`tsc -b && vite build`) |
| `mvn package -DskipTests` | **BUILD SUCCESS** |

Failed tests (do not treat the suite as green):

| Class | Method | Kind |
|-------|--------|------|
| `ContextOwnershipResolverTest` | `insideTypoCompilesToContainerContext` | Failure (`expected true was false`) |
| `FilterEngineRegressionTest` | `discoversBrandCheckboxAndPriceRangeOnGenericPage` | Failure |
| `FilterOwnedOptionRegressionTest` | `prefersBrandAkCheckboxOverHeaderText` | Failure |
| `EvidenceImageCompressorTest` | `shrinksLargePngForMultimodalAi` | Failure |
| `DomGraphOwnershipTest` | `brandOwnedHpOutranksProductCardHp` | **Error** (`IndexOutOfBoundsException` index 1, length 1) |

Skipped (env-gated, expected without flags): `RagPgVectorIntegrationTest`, `WorkspaceUiSmokeTest`, `OrangeHrMRealRegressionTest`, `RealWorldMatrixRegressionTest` (3), `SearchRealRegressionTest`, `UrbanCompanyRuntimeRealRegressionTest`.

## Runtime (this machine, audit moment)

- Ports **8081** / **5300**: **not listening**
- `java` / `node`: **not listed**
- Workspace: **was not a git repo** at audit start
- Katalyst: **not in this workspace**; not modified

## Gemini / Ollama / RAG at runtime

Not probed live in this pass (servers were down). Code defaults: Gemini primary with key pool; Ollama fallback; RAG enabled in config but empty/unusable without Postgres+pgvector+embed model. Document runtime health only after `/api/health` and `/api/health/ai` are actually called.

## Production verdict

**NOT PRODUCTION-READY.**
