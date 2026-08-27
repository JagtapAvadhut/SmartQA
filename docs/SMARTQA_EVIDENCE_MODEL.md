# Evidence model

Evidence is what SmartQA **observed**, not what AI guessed.

## Sources

| Source | Producer | Executor? |
|--------|----------|-----------|
| Playwright DOM | `DomExtractor` | No |
| Bounding boxes / visibility | same extract + Playwright | No |
| CDP DOMSnapshot | `CdpBrowserIntelligence` | No |
| Accessibility tree | `Accessibility.getFullAXTree` via CDP | No |
| Screenshot PNG | `ScreenshotService` | No |
| Network | `NetworkObservation` | No (metadata only; bodies not stored) |
| Console | captured into inspect snapshot | No |

## Structures

- **Tree:** `ElementTree` — parent/child/depth over the candidate list.
- **Graph:** `CandidateRelationshipGraph` — typed edges (containment, heading, …).
- **PhysicalControl:** role/type/enabled/value/capabilities for ranking and safety.
- **Locator memory document:** stored on `test_cases.locator_memory` after a generation session (JSON text). Secrets stripped.

## Moments

`BrowserIntelligenceService` can reuse a snapshot for ~450ms on the same URL so rank/filter/search see one moment. CDP-forced inspect bypasses that cache.

## What evidence is not

- Not a license to skip `ActionabilityVerifier`.
- Not a substitute for Playwright.
- RAG snippets are **not** live evidence.
