# SmartQA

SmartQA turns an application URL plus natural-language instructions into a Playwright Java test. The current workspace is a Spring Boot WebFlux backend (`Backend/smartqa`) and a Vite/React UI (`UI/smartqa_ui`).

This README describes the **current source**, not older GitHub snapshots. The GitHub repository historically contained an unrelated Spring/React starter (`spring/`, `react/`). That tree is not this product.

## What it does

1. Accepts a URL and instructions (paragraph or structured steps).
2. Compiles an intent contract.
3. Opens Chromium through **Playwright Java**.
4. Builds browser evidence (DOM, optional CDP/AX, tree, graph, physical controls).
5. Resolves a target, optionally asks Gemini/Ollama to **reason**, then a **safety gate** checks the live candidate.
6. **Playwright executes** the action. AI does not click, type, or navigate.
7. Emits Playwright Java, runs a static **quality gate**, then an **independent generated-test validator**.
8. Can run the generated test again as a normal execution.

## Layout

| Path | Role |
|------|------|
| `Backend/smartqa` | Spring Boot 4.1 / Java 21 API, Playwright, Flyway, tests |
| `UI/smartqa_ui` | React 19 UI (workspace, tests, projects, settings) |
| `docs/` | Architecture and status, kept in sync with source |
| `.ui-e2e/` | Local HTML fixtures and captured request JSON — not a CI e2e suite |

There is **no Katalyst project** in this workspace. Do not add one.

## Status (honest)

**Not production-ready.** Default `mvn test` on 2026-08-27: **514** run, **4** failures, **1** error, **8** skipped. UI `npm run build` passed. Live-site matrix was not executed.

| Area | Evidence |
|------|---------|
| Backend compile / unit+integration tests | This session: **514** run, **4** failed, **1** error, **8** skipped. Details in [docs/SMARTQA_CURRENT_STATUS.md](docs/SMARTQA_CURRENT_STATUS.md) |
| UI production build | `npm run build` in `UI/smartqa_ui` |
| Default real-browser matrix | **Not run** unless env flags are set |
| Playwright MCP executor | Class exists; **disabled** and **does not execute** even if the MCP URL is reachable |
| RAG | Implemented (pgvector); advisory only; needs Postgres + embeddings |
| Schedule / Jira | Tables exist; **disabled** by default |
| “All websites” / “100% accuracy” | **Not claimed** |

## Runtime defaults (from `application.yml`)

| Setting | Default |
|--------|---------|
| API port | `8081` (`SMARTQA_PORT`) |
| UI origin / Vite | `http://localhost:5300` |
| DB | PostgreSQL `smartqa` on localhost |
| Browser provider | `playwright` (Java), Chromium, **headed**, page zoom 50% |
| MCP | `smartqa.mcp.enabled=false` |
| AI primary | `gemini` (`gemini-flash-latest`) |
| AI fallback | `ollama` (`qwen2.5-coder:3b` at `http://localhost:11434`) |
| CDP | enabled, snapshot mode `ESCALATE` |
| RAG | enabled, Ollama `nomic-embed-text` / 768-d, cosine, top-k 5, threshold 0.55 |

Secrets belong in environment variables (`GEMINI_API_KEY`, `GEMINI_API_KEYS`, `SMARTQA_DB_PASSWORD`, …). Do not commit them.

## Run locally

Postgres with `pgvector` must exist before the API starts (Flyway V6 creates `smartqa_knowledge`).

```bash
# Backend
cd Backend/smartqa
mvnw.cmd spring-boot:run

# UI (separate terminal)
cd UI/smartqa_ui
npm install
npm run dev
```

Open `http://localhost:5300`. The Vite dev server proxies `/api` to `http://localhost:8081`.

## Tests

```bash
cd Backend/smartqa
mvnw.cmd test
mvnw.cmd package -DskipTests
```

```bash
cd UI/smartqa_ui
npm run build
```

Real-browser / live-site tests are **opt-in** (examples: `SMARTQA_REAL_MATRIX=true`, `SMARTQA_ORANGEHRM_REAL=true`, `SMARTQA_URBANCOMPANY_REAL=true`, `SMARTQA_SEARCH_REAL=true`, `SMARTQA_RAG_IT=true`, `SMARTQA_UI_SMOKE=true`). They are skipped in a normal developer run.

## Documentation

Start with:

- [docs/SMARTQA.md](docs/SMARTQA.md) — product and architecture
- [docs/SMARTQA_CURRENT_STATUS.md](docs/SMARTQA_CURRENT_STATUS.md) — what is implemented vs tested
- [docs/SMARTQA_API_CATALOG.md](docs/SMARTQA_API_CATALOG.md) — HTTP API
- [docs/SMARTQA_RUNTIME_FLOW.md](docs/SMARTQA_RUNTIME_FLOW.md) — request path through the engine
