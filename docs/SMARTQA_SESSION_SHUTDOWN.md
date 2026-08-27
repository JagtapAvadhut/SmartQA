# Session / process state

Verified on this documentation pass (Windows, workspace `d:\Smart_QA`).

## Git

The workspace **was not a git repository** (`fatal: not a git repository`). GitHub `https://github.com/JagtapAvadhut/SmartQA.git` exists separately with default branch **`master`** and an **old** tree (`spring/`, `react/`, starter `README.md`). That remote is not this product until a replacement push completes.

## Listeners

No process was bound to **8081** or **5300** when checked (`Get-NetTCPConnection` empty). No `java` / `node` processes were listed.

**Do not read this file as “servers were stopped by an operator.”** It means: at audit time, the API and Vite UI were **not running**.

## How to start (when you want them)

```bash
cd Backend/smartqa
mvnw.cmd spring-boot:run
```

```bash
cd UI/smartqa_ui
npm run dev
```

Requires PostgreSQL `smartqa` (with `pgvector` for RAG). Gemini and/or Ollama as configured.

## Local artifacts (not servers)

- `Backend/smartqa/target/` — last Maven compile/test output (if present)
- `UI/smartqa_ui/node_modules/` — npm install
- `.ui-e2e/` — local HTML fixtures and captured request JSON

## Secrets

No `.env` files were found under the workspace during the audit search. Do not commit `.env` if you create one.
