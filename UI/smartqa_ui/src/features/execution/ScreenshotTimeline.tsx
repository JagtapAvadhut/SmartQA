import { useEffect, useState } from 'react'
import { getScreenshots, screenshotUrl, type ScreenshotMeta } from '../../api/screenshots'

interface ScreenshotTimelineProps {
  runId: string | null
}

export function ScreenshotTimeline({ runId }: ScreenshotTimelineProps) {
  const [screenshots, setScreenshots] = useState<ScreenshotMeta[]>([])
  const [selected, setSelected] = useState<ScreenshotMeta | null>(null)

  useEffect(() => {
    if (!runId) return
    const interval = setInterval(() => {
      getScreenshots(runId).then(setScreenshots).catch(() => {})
    }, 3000)
    getScreenshots(runId).then(setScreenshots).catch(() => {})
    return () => clearInterval(interval)
  }, [runId])

  if (screenshots.length === 0) {
    return (
      <article className="card">
        <h2>Browser Timeline</h2>
        <p className="muted">Waiting for browser screenshots…</p>
      </article>
    )
  }

  return (
    <article className="card">
      <h2>Browser Timeline</h2>
      <div style={{ display: 'flex', gap: 8, overflowX: 'auto', padding: '8px 0' }}>
        {screenshots.map((s) => (
          <button
            key={s.id}
            type="button"
            onClick={() => setSelected(s)}
            style={{
              border: selected?.id === s.id ? '2px solid var(--accent)' : '1px solid var(--panel-border)',
              borderRadius: 6,
              padding: 2,
              background: 'none',
              cursor: 'pointer',
              flexShrink: 0,
            }}
          >
            <img
              src={screenshotUrl(s.id)}
              alt={`Step ${s.stepNumber}`}
              style={{ width: 120, height: 80, objectFit: 'cover', borderRadius: 4 }}
            />
            <div style={{ fontSize: 11, textAlign: 'center' }}>Step {s.stepNumber}</div>
          </button>
        ))}
      </div>
      {selected ? (
        <div style={{ marginTop: 12 }}>
          <dl className="kv compact-kv">
            <div><dt>Step</dt><dd>{selected.stepNumber}</dd></div>
            <div><dt>Event</dt><dd>{selected.eventType}</dd></div>
            <div><dt>URL</dt><dd className="wrap">{selected.url}</dd></div>
            <div><dt>Time</dt><dd>{new Date(selected.timestamp).toLocaleTimeString()}</dd></div>
            {selected.evidenceMomentId ? (
              <div><dt>Evidence moment</dt><dd><code>{selected.evidenceMomentId}</code></dd></div>
            ) : null}
          </dl>
          <img
            src={screenshotUrl(selected.id)}
            alt={`Step ${selected.stepNumber} full`}
            style={{ maxWidth: '100%', borderRadius: 6, border: '1px solid var(--border)', marginTop: 8 }}
          />
        </div>
      ) : null}
    </article>
  )
}
