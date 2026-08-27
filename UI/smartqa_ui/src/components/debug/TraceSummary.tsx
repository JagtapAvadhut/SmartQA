import type { TraceEvent } from '../../services/traceLogger'

interface TraceSummaryProps {
  traceId: string
  status: string
  events: number
  errors: number
  warnings: number
  duration: string
}

export function TraceSummary({ traceId, status, events, errors, warnings, duration }: TraceSummaryProps) {
  return (
    <dl className="kv compact-kv trace-summary">
      <div>
        <dt>Trace ID</dt>
        <dd className="wrap">{traceId}</dd>
      </div>
      <div>
        <dt>Status</dt>
        <dd>{status}</dd>
      </div>
      <div>
        <dt>Events</dt>
        <dd>{events}</dd>
      </div>
      <div>
        <dt>Errors</dt>
        <dd>{errors}</dd>
      </div>
      <div>
        <dt>Warnings</dt>
        <dd>{warnings}</dd>
      </div>
      <div>
        <dt>Duration</dt>
        <dd>{duration || '—'}</dd>
      </div>
    </dl>
  )
}

export function summarizeEvents(events: TraceEvent[]): Omit<TraceSummaryProps, 'traceId'> {
  const errors = events.filter((event) => event.level === 'ERROR').length
  const warnings = events.filter((event) => event.level === 'WARN').length
  const durationMs = events.reduce((sum, event) => sum + (event.durationMs ?? 0), 0)
  const failed = errors > 0
  const ended = events.some((event) => event.operation === 'TRACE_END')
  return {
    status: failed ? 'FAILED' : ended ? 'COMPLETED' : events.length === 0 ? 'IDLE' : 'RUNNING',
    events: events.length,
    errors,
    warnings,
    duration: durationMs > 0 ? `${(durationMs / 1000).toFixed(2)}s` : '',
  }
}
