import type { ProgressEvent } from '../../types/intent'
import { screenshotUrl } from '../../api/screenshots'

interface LiveBrowserViewProps {
  events: ProgressEvent[]
  applicationUrl: string
  onStop?: () => void
  stopping?: boolean
}

function latest(events: ProgressEvent[], key: string): string {
  for (let i = events.length - 1; i >= 0; i--) {
    const v = events[i]?.details?.[key]
    if (typeof v === 'string' && v.length > 0) return v
    if (typeof v === 'number') return String(v)
  }
  return ''
}

function latestField(events: ProgressEvent[], field: keyof ProgressEvent): string {
  for (let i = events.length - 1; i >= 0; i--) {
    const v = events[i]?.[field]
    if (typeof v === 'string' && v.length > 0) return v
    if (typeof v === 'number') return String(v)
  }
  return ''
}

/** @deprecated Prefer LiveBrowserPanel / ScreenshotTimeline in primary flow. Kept for reuse. */
export function LiveBrowserView({ events, applicationUrl, onStop, stopping }: LiveBrowserViewProps) {
  const last = events.at(-1)
  const stepNumber = latestField(events, 'stepNumber') || '—'
  const totalSteps = latestField(events, 'totalSteps') || '—'
  const currentUrl = latestField(events, 'currentUrl') || latest(events, 'url') || applicationUrl
  const pageTitle = latestField(events, 'pageTitle') || latest(events, 'title') || '—'
  const provider = latestField(events, 'executionProvider') || 'PLAYWRIGHT_JAVA'
  const action = latest(events, 'action') || (last?.message ?? '—')
  const locator = latest(events, 'locator') || '—'
  const controlType = latest(events, 'controlType') || '—'
  const confidence = latest(events, 'confidence')
  const lastScreenshotId = latestField(events, 'screenshotId')
  const isRunning = last && !['EXECUTION_COMPLETED', 'EXECUTION_FAILED', 'EXECUTION_STOPPED'].includes(last.type)

  return (
    <article className="card">
      <h2>Live Browser</h2>
      <dl className="kv compact-kv">
        <div><dt>Current URL</dt><dd className="wrap">{currentUrl || '—'}</dd></div>
        <div><dt>Page</dt><dd>{pageTitle}</dd></div>
        <div><dt>Browser</dt><dd>Chromium</dd></div>
        <div><dt>Status</dt><dd className={isRunning ? 'ok-text' : 'muted'}>{isRunning ? 'RUNNING' : last?.type === 'EXECUTION_COMPLETED' ? 'COMPLETED' : last?.type === 'EXECUTION_STOPPED' ? 'STOPPED' : last?.type === 'EXECUTION_FAILED' ? 'FAILED' : 'IDLE'}</dd></div>
        <div><dt>Current step</dt><dd>{stepNumber} / {totalSteps}</dd></div>
        <div><dt>Action</dt><dd>{action}</dd></div>
        <div><dt>Locator</dt><dd className="wrap"><code>{locator}</code></dd></div>
        <div><dt>Control</dt><dd>{controlType}</dd></div>
        <div><dt>Confidence</dt><dd>{confidence ? `${(Number(confidence) * 100).toFixed(0)}%` : '—'}</dd></div>
        <div><dt>Provider</dt><dd>{provider}</dd></div>
      </dl>
      {lastScreenshotId ? (
        <div style={{ marginTop: 12 }}>
          <img src={screenshotUrl(lastScreenshotId)} alt="Browser screenshot" style={{ maxWidth: '100%', borderRadius: 6, border: '1px solid var(--border)' }} />
        </div>
      ) : null}
      {isRunning && onStop ? (
        <div className="toolbar" style={{ marginTop: 8 }}>
          <button className="btn" type="button" onClick={onStop} disabled={stopping}>
            {stopping ? 'Stopping…' : 'Stop'}
          </button>
        </div>
      ) : null}
    </article>
  )
}
