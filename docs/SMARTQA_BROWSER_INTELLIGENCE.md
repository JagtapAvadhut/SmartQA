# Browser intelligence

Package roots: `com.smartqa.browser`, `com.smartqa.browser.intelligence`, `com.smartqa.browser.intelligence.cdp`, `com.smartqa.browser.multimodal`.

## Components that exist

| Class | Package | Role |
|-------|---------|------|
| `DomExtractor` | `browser.intelligence` | Playwright DOM → `ElementCandidate` list |
| `BrowserIntelligenceService` | `browser.intelligence` | Orchestrates inspect + same-moment cache (~450ms) |
| `ElementTree` | `browser.intelligence` | Hierarchy over the same candidates |
| `CandidateRelationshipGraph` | `browser.multimodal` | Typed parent/child/heading/container edges |
| `PhysicalControl` | `browser.intelligence` | Normalized control projection |
| `TreeGraphReconciler` | `browser.intelligence` | Reconciles tree vs graph consistency |
| `LocatorRanker` | `browser.intelligence` | Scores candidates; explainable breakdown |
| `HardConstraintChecker` | `browser.intelligence` | Fail-closed hard constraints |
| `ElementResolver` | `browser` | Resolves a step to a locator (may raise clarification) |
| `ActionabilityVerifier` | `browser` | Visible / enabled / not covered |
| `FilterEngine` | `browser` | Generic filter expand / own / apply / verify |
| `SearchIntelligence` | `browser` | Search input → suggestions → submit → state |
| `CdpBrowserIntelligence` | `browser.intelligence.cdp` | `DOMSnapshot` + `Accessibility.getFullAXTree` |
| `VisualRegionAnalyzer` | `browser.multimodal` | Screenshot regions for visual targets |
| `MultimodalTargetDiscoveryEngine` | `browser.multimodal` | AI target hypothesis from compact evidence |
| `TargetSafetyGate` | `browser.multimodal` | Live verification of AI hypothesis |
| `ExecutionMemoryService` | `browser.intelligence.memory` | In-memory advisory locator memory (max 200) |
| `ScreenshotService` | `execution.screenshot` | PNG capture keyed by execution run |
| `LocatorHealingResolver` | `browser` | Locator healing during execution |
| `PageStateWatcher` | `browser.intelligence` | Page readiness / mutations |
| `StateTransitionVerifier` | `browser.intelligence` | Post-action state check |
| `PageRecoveryPlanner` | `browser.intelligence.recovery` | Recovery plan from `BrowserStateHistory` |

Implemented names in this source: `FilterEngine` and `SearchIntelligence`.

## Inspect path

`BrowserIntelligenceService.inspect(Page, consoleErrors, captureCdp)`:

1. `DomExtractor.extract(page)` (retry once on transient navigation).
2. Optionally CDP capture + `CdpCandidateEnricher`.
3. Build `ElementTree`, `CandidateRelationshipGraph`, `PhysicalControl` list.
4. Return `BrowserSnapshot` (versioned). Same URL within 450ms can reuse the snapshot unless CDP is forced.

## Ranking and constraints

`LocatorRanker` scores candidates using role, accessible name, text, ownership, visibility, and graph/tree context. `HardConstraintChecker` can eliminate candidates (wrong type, covered, wrong container). `FailClosedDecision` records why execution must not proceed.

## Filters and search

`FilterEngine` discovers filter containers and options from live candidates. It does not ship site-specific CSS. `SearchIntelligence` fills a search field, may confirm autocomplete, and checks that page state actually changed. Fill+Enter alone is not treated as success.

## Memory

`ExecutionMemoryService` stores successful locator hints in process memory (`CopyOnWriteArrayList`), scoped by application host. Secrets in the target or locator string are dropped. Live DOM always outranks memory. Nothing is written to PostgreSQL for this cache.

## Screenshots

`ScreenshotService` writes PNGs under `smartqa.screenshots.base-dir` (default `./screenshots`). Mode `IMPORTANT` (default) skips uninteresting events; `OFF` captures nothing. Max 100 images per run in the in-memory index. `GET /api/screenshots/{id}` serves PNG.
