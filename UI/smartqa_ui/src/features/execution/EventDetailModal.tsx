import type { ProgressEvent } from '../../types/intent'
import { screenshotUrl } from '../../api/screenshots'
import { eventLabel } from '../workspace/eventLabels'

interface EventDetailModalProps {
  event: ProgressEvent
  onClose: () => void
}

export function EventDetailModal({ event, onClose }: EventDetailModalProps) {
  const d = event.details || {}
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex',
      alignItems: 'center', justifyContent: 'center', zIndex: 1000,
    }} onClick={onClose}>
      <div className="card" style={{ maxWidth: 600, maxHeight: '80vh', overflow: 'auto' }} onClick={(e) => e.stopPropagation()}>
        <h2>{eventLabel(event.type)}</h2>
        <dl className="kv compact-kv">
          <div><dt>Event</dt><dd>{event.type}</dd></div>
          <div><dt>Message</dt><dd>{event.message}</dd></div>
          {event.stepNumber ? <div><dt>Step</dt><dd>{event.stepNumber}{event.totalSteps ? ` / ${event.totalSteps}` : ''}</dd></div> : null}
          {event.currentUrl ? <div><dt>URL</dt><dd className="wrap">{event.currentUrl}</dd></div> : null}
          {event.pageTitle ? <div><dt>Page</dt><dd>{event.pageTitle}</dd></div> : null}
          {event.executionProvider ? <div><dt>Provider</dt><dd>{event.executionProvider}</dd></div> : null}
          <div><dt>Time</dt><dd>{new Date(event.timestamp).toLocaleTimeString()}</dd></div>
          {d.action ? <div><dt>Action</dt><dd>{d.action as string}</dd></div> : null}
          {d.locator ? <div><dt>Locator</dt><dd className="wrap"><code>{d.locator as string}</code></dd></div> : null}
          {d.controlType ? <div><dt>Control</dt><dd>{d.controlType as string}</dd></div> : null}
          {d.confidence != null ? <div><dt>Confidence</dt><dd>{(Number(d.confidence) * 100).toFixed(0)}%</dd></div> : null}
          {d.candidateId ? <div><dt>Candidate</dt><dd><code>{String(d.candidateId)}</code></dd></div> : null}
          {d.controlId ? <div><dt>Control</dt><dd><code>{String(d.controlId)}</code></dd></div> : null}
          {d.explanation || d.whySelected ? <div><dt>Why selected</dt><dd className="wrap">{String(d.explanation || d.whySelected)}</dd></div> : null}
          {d.scoreBreakdown ? <div><dt>Score breakdown</dt><dd className="wrap">{String(d.scoreBreakdown)}</dd></div> : null}
          {d.safetyGate ? <div><dt>Safety Gate</dt><dd>{String(d.safetyGate)}</dd></div> : null}
          {d.stateBefore ? <div><dt>State before</dt><dd className="wrap">{String(d.stateBefore)}</dd></div> : null}
          {d.stateAfter ? <div><dt>State after</dt><dd className="wrap">{String(d.stateAfter)}</dd></div> : null}
          {d.assertion ? <div><dt>Assertion</dt><dd className="wrap">{String(d.assertion)}</dd></div> : null}
          {d.evidenceMomentId ? <div><dt>Evidence moment</dt><dd><code>{String(d.evidenceMomentId)}</code></dd></div> : null}
          {d.aiInvoked != null ? <div><dt>AI invoked</dt><dd>{String(d.aiInvoked)}</dd></div> : null}
          {d.provider ? <div><dt>AI provider</dt><dd>{String(d.provider)}</dd></div> : null}
          {d.durationMs != null ? <div><dt>Duration</dt><dd>{d.durationMs as number}ms</dd></div> : null}
          {d.executionProvider ? <div><dt>Provider</dt><dd>{d.executionProvider as string}</dd></div> : null}
        </dl>
        {event.screenshotId ? (
          <div style={{ marginTop: 12 }}>
            <img src={screenshotUrl(event.screenshotId)} alt="Screenshot" style={{ maxWidth: '100%', borderRadius: 6, border: '1px solid var(--border)' }} />
          </div>
        ) : null}
        <div className="toolbar" style={{ marginTop: 12 }}>
          <button className="btn" type="button" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
