# Browser intelligence — what is in source

This is not a roadmap. It lists classes that **exist** under `com.smartqa.browser`.

## Present

| User-facing name | Actual class |
|------------------|--------------|
| DOM extract | `DomExtractor` |
| Tree | `ElementTree` |
| Graph | `CandidateRelationshipGraph` |
| Physical control | `PhysicalControl` |
| Ranker | `LocatorRanker` |
| Resolver | `ElementResolver` |
| Actionability | `ActionabilityVerifier` |
| Filter engine | `FilterEngine` |
| Search intelligence | `SearchIntelligence` |
| CDP | `CdpBrowserIntelligence` |
| Visual regions | `VisualRegionAnalyzer` |
| Multimodal discovery | `MultimodalTargetDiscoveryEngine` |
| Safety gate | `TargetSafetyGate` |
| Execution memory | `ExecutionMemoryService` |
| Screenshots | `ScreenshotService` |
| Network facts | `NetworkObservation` (masked URL/status; no bodies) |
| Recovery planner | `PageRecoveryPlanner` + `BrowserStateHistory` |
| Healing | `LocatorHealingResolver` |

## Behaviour that is implemented

- Inspect builds one candidate inventory, then tree + graph + physical controls from it.
- Same-moment snapshot cache exists in `BrowserIntelligenceService` (short TTL).
- Hard constraints can fail closed (`HardConstraintChecker`, `FailClosedDecision`).
- AI hypothesis must match a **current** live candidate id or `TargetSafetyGate` rejects.
- Filter/search helpers are generic (no Flipkart-only selectors in those classes).

## Not implemented / not claimed

- MCP as a working executor
- Persistent execution memory in PostgreSQL (memory is in-process)
- CDP on Firefox/WebKit
- Perfect ranking on every site
