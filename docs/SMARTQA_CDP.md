# CDP and accessibility

## Class

`com.smartqa.browser.intelligence.cdp.CdpBrowserIntelligence`

Playwright remains the executor. CDP is **evidence**.

## Config (`application.yml` → `smartqa.intelligence`)

| Key | Env | Default |
|-----|-----|---------|
| `cdp-enabled` | `SMARTQA_CDP_ENABLED` | `true` |
| `cdp-timeout-ms` | `SMARTQA_CDP_TIMEOUT_MS` | `4000` |
| `cdp-snapshot-mode` | `SMARTQA_CDP_SNAPSHOT_MODE` | `ESCALATE` |

`CdpSnapshotMode` behavior in `SmartQaProperties.Intelligence`:

- `ALWAYS` — capture on every inspect (`captureCdpOnInspect()`).
- `ESCALATE` (default) — capture when inspect asks for CDP / escalation (`captureCdpOnEscalate()` is true unless mode is `OFF`).
- `OFF` — `CdpCapture.unavailable("cdp_disabled")`.

## Capture

On Chromium, `CdpBrowserIntelligence.capture(Page)`:

1. `page.context().newCDPSession(page)` (or reuse).
2. `DOM.enable`, `Accessibility.enable`.
3. `DOMSnapshot.captureSnapshot` (DOM rects, paint order).
4. `Accessibility.getFullAXTree`.
5. Parse via `CdpSnapshotParser` into `CdpCapture` + `AccessibilityNode` list.
6. `CdpCandidateEnricher` may attach AX/CDP fields onto `ElementCandidate`.

If Playwright throws, the run continues: `CdpCapture.unavailable(reason)`. Docs must not say “CDP off” while `SMARTQA_CDP_ENABLED=true`.

## Non-Chromium

Firefox/WebKit have no equivalent path. Capture is skipped/unavailable. That is **not** a test failure.

## Related types

- `AccessibilityNode`
- `CdpCapture`
- `CdpSnapshotParser`
- `CdpCandidateEnricher`
- `NormalizedDomNode`, `DomGraph` (CDP-side graph, still evidence)
