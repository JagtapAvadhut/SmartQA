# Architecture gaps (current, not closed by this doc)

This file records **remaining** gaps. It does not claim they are fixed.

| Gap | Evidence |
|------|----------|
| MCP executor | `PlaywrightMcpBrowserExecutionProvider.execute` throws; default `mcp.enabled=false` |
| Generation durability | No `generation_runs` table; registry is memory |
| Pipeline heartbeat | Comment on `ABANDONED`: no durable heartbeat |
| Health/generation | Static JSON, not a browser probe |
| Real-site matrix | Env-gated tests; not default CI |
| Memory persistence | `ExecutionMemoryService` in-memory only |
| CDP non-Chromium | Unavailable capture, non-fatal |
| Schedule / Jira | Disabled flags; tables only |
| Embedding/RAG outage | Pipeline continues; retrieval empty |
| `/api/health/generation` vs true Playwright install | Can report UP without proving browsers installed |

Closed **in code** (do not re-list as missing): DOM tree, relationship graph, physical control, CDP enrich, safety gate, key pool, independent validator, SSE correlation fields, secret masking.
