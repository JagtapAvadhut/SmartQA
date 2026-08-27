# Locator resolution

## Order (as coded)

1. Intent step (`IntentStep` / structured step): action, target text, optional value, assertion, location **hint**.
2. `DomExtractor` builds `ElementCandidate` list from Playwright.
3. Optional CDP enrich (`CdpCandidateEnricher`).
4. `LocatorRanker` scores; `HardConstraintChecker` drops illegal candidates.
5. Ownership / container / heading context from tree + graph.
6. If score ≥ `smartqa.intelligence.ai-escalation-threshold` (default **0.70**) and constraints pass → use that candidate.
7. Else optional `MultimodalTargetDiscoveryEngine` (Gemini/Ollama) proposes a **live candidate id**.
8. `TargetSafetyGate` verifies id ∈ current ranked set, visible, enabled, ownership, compatibility.
9. `ElementResolver` / Playwright locator from ranked candidate (`LocatorSelectorBuilder`).
10. `ActionabilityVerifier` then Playwright action.
11. `StateTransitionVerifier` / assertions.

## Fail closed

Ambiguous or incompatible targets raise `ClarificationRequiredException` (HTTP 409) or fail the step. Runtime clarifications persist in `runtime_clarifications`.

## Healing

`LocatorHealingResolver` may retry alternate locators from memory **after** a miss. Live DOM still wins. Healing is not MCP.

## Generated code

`DeterministicPlaywrightFactory` prefers the resolved locators. `QualityGateService` rejects Selenium, `Thread.sleep`, process APIs, etc. That is **not** live validation.
