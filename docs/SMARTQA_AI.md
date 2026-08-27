# AI (Gemini, key pool, Ollama)

AI **reasons**. `TargetSafetyGate` **verifies**. Playwright **executes**.

## Providers

| Class | Id | Role |
|-------|-----|------|
| `GeminiAiProvider` | `gemini` | Primary chat / structured JSON / multimodal |
| `OllamaAiProvider` | `ollama` | Local fallback |
| `OpenAiCompatibleAiProvider` | `openai-compatible` | Optional; unused unless configured |
| `FallbackAiProvider` | wrapper | Primary then fallback |

Config (`smartqa.ai`):

- `provider` / `primary-provider`: default `gemini`
- `fallback-provider`: default `ollama`
- Timeouts: `timeout-seconds` 180, `intent-timeout-seconds` 45, `connect-timeout-seconds` 10
- `max-retries`: 1
- `consensus-enabled`: true (second opinion on important failures)
- `consensus-low-confidence`: 0.65

## Gemini key pool

`GeminiKeyPool` is the only rotation implementation. It logs **keyIndex**, never the secret.

Keys are merged from:

1. `GEMINI_API_KEY` / `smartqa.ai.gemini.api-key`
2. `GEMINI_API_KEYS` (comma-separated)
3. `GEMINI_API_KEY_2` … `GEMINI_API_KEY_8`

`GeminiFailureKind`: `RATE_LIMITED`, `SERVER_ERROR`, `TIMEOUT`, `AUTH`, `SCHEMA`, `UNKNOWN`.

`GeminiRotationPolicy` defaults:

| Failure | Cooldown |
|---------|----------|
| 429 / `RATE_LIMITED` | 60s (`SMARTQA_GEMINI_COOLDOWN_SECONDS`) |
| 5xx / timeout / unknown | 15s (`SMARTQA_GEMINI_TRANSIENT_COOLDOWN_SECONDS`) |
| 401/403 `AUTH` | 300s (`SMARTQA_GEMINI_INVALID_KEY_COOLDOWN_SECONDS`) |
| `SCHEMA` (bad JSON) | **no cooldown** — key stays `HEALTHY` |

Rotation: `SMARTQA_GEMINI_ROTATION_ENABLED` default true; `max-key-attempts` default `all`; `retry-per-key` 1.

When every key is cooling down, Gemini is exhausted. `FallbackAiProvider` may then call Ollama if configured.

## Ollama

- Base URL: `OLLAMA_BASE_URL` default `http://localhost:11434`
- Chat model: `OLLAMA_MODEL` default `qwen2.5-coder:3b`
- If Gemini has **no keys**, `FallbackAiProvider` treats primary cloud as unusable and uses the fallback id.

Ollama down is an environment failure, not a SmartQA product defect.

## Structured output and screenshots

`AiMediaPart` / `AiPrompt` carry optional images. `EvidenceImageCompressor` shrinks screenshots before multimodal calls. Structured JSON is expected for target hypotheses and failure diagnosis. Schema failures do **not** burn a Gemini key.

## Consensus

On important failures, `ConsensusResolver` may request Gemini diagnosis plus an Ollama second opinion when `smartqa.ai.consensus-enabled` is true and confidence is below 0.65.

## What AI must not do

- No `page.click` / `fill` / `goto` from an AI provider class.
- No MCP tool execution in v1.
- RAG hits are prompt context only (`RagRetrievalService`).
