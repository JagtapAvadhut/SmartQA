# AI escalation

Threshold: `smartqa.intelligence.ai-escalation-threshold` default **0.70**.

When deterministic ranking is below threshold, or target is visual/ambiguous, `MultimodalTargetDiscoveryEngine` may call `FallbackAiProvider` with compact DOM/graph/screenshot evidence (`EvidenceImageCompressor`).

## Rules in source

- AI returns a hypothesis (`TargetHypothesis`) with a **live candidate id**, not a CSS selector to execute blindly.
- `TargetSafetyGate` rejects missing id, unsafe strategy, covered/disabled nodes, ownership mismatch, capability mismatch, visual region miss.
- Rejected hypothesis ≠ Playwright click.
- Gemini schema failures do **not** cooldown a key (`GeminiFailureKind.SCHEMA`).
- Exhausted Gemini pool can fall through to Ollama (`FallbackAiProvider`).
- Consensus (`smartqa.ai.consensus-enabled`) may ask a second provider on important failures.

## Not escalation

Quality gate compile errors are static. Independent validator failures are browser facts. RAG is optional context, not an escalation executor.
