import type { ProgressEvent } from '../../types/intent'
import { eventLabel, GENERATE_STEPS } from '../workspace/eventLabels'
import { formatLocator } from '../workspace/session'

interface LiveBrowserPanelProps {
  events: ProgressEvent[]
  applicationUrl: string
  browser?: string
  provider?: string
}

function latestDetail(events: ProgressEvent[], key: string): string {
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const value = events[index]?.details?.[key]
    if (typeof value === 'string' && value.length > 0) {
      return value
    }
    if (typeof value === 'number') {
      return String(value)
    }
  }
  return '—'
}

export function LiveBrowserPanel({
  events,
  applicationUrl,
  browser = 'Chromium',
  provider = 'Ollama',
}: LiveBrowserPanelProps) {
  const latest = events.at(-1)
  const locatorType = latestDetail(events, 'locatorType')
  const locator = latestDetail(events, 'locator')
  return (
    <article className="card">
      <h2>Browser intelligence</h2>
      <dl className="kv compact-kv">
        <div>
          <dt>Application URL</dt>
          <dd className="wrap">{applicationUrl || '—'}</dd>
        </div>
        <div>
          <dt>Current URL</dt>
          <dd className="wrap">{latestDetail(events, 'url')}</dd>
        </div>
        <div>
          <dt>Page</dt>
          <dd>{latestDetail(events, 'title')}</dd>
        </div>
        <div>
          <dt>Current action</dt>
          <dd>{latest ? eventLabel(latest.type, latest.message) : 'Idle'}</dd>
        </div>
        <div>
          <dt>Browser</dt>
          <dd>{browser}</dd>
        </div>
        <div>
          <dt>AI provider</dt>
          <dd>{provider}</dd>
        </div>
        <div>
          <dt>Interactive elements</dt>
          <dd>{latestDetail(events, 'interactiveCount')}</dd>
        </div>
        <div>
          <dt>Locator</dt>
          <dd className="wrap">
            {locator === '—' ? '—' : formatLocator(locatorType === '—' ? null : locatorType, locator)}
          </dd>
        </div>
        <div>
          <dt>Confidence</dt>
          <dd>{latestDetail(events, 'confidence')}</dd>
        </div>
        <div>
          <dt>Why selected</dt>
          <dd className="wrap">{latestDetail(events, 'whySelected') !== '—'
            ? latestDetail(events, 'whySelected')
            : latestDetail(events, 'explanation')}</dd>
        </div>
        <div>
          <dt>Safety Gate</dt>
          <dd>{latestDetail(events, 'safetyGate')}</dd>
        </div>
      </dl>
      <ol className="progress-list">
        {GENERATE_STEPS.map((step) => {
          const types = new Set(events.map((event) => event.type))
          const done = types.has(step.type) || types.has('GENERATION_COMPLETE')
          const current = latest?.type === step.type
          return (
            <li key={step.type} className={done ? 'done' : current ? 'current' : ''}>
              <span>{done ? '✓' : current ? '●' : '○'}</span> {step.label}
            </li>
          )
        })}
      </ol>
    </article>
  )
}
