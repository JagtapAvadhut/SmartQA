import type { TraceEvent } from '../../services/traceLogger'

interface TraceEventRowProps {
  event: TraceEvent
}

export function TraceEventRow({ event }: TraceEventRowProps) {
  const time = event.timestamp?.slice(11, 23) || ''
  const meta = event.metadata
    ? Object.entries(event.metadata)
        .slice(0, 6)
        .map(([key, value]) => `${key}=${typeof value === 'string' ? value : JSON.stringify(value)}`)
        .join(' ')
    : ''
  return (
    <div className={`trace-row level-${event.level.toLowerCase()}`}>
      <span className="trace-time">{time}</span>
      <span className="trace-level">{event.level}</span>
      <span className="trace-component">{event.component}</span>
      <span className="trace-operation">{event.operation}</span>
      <span className="trace-message">
        {event.message}
        {meta ? <span className="muted"> {meta}</span> : null}
        {event.error ? <span className="error-text"> {event.error}</span> : null}
      </span>
    </div>
  )
}
