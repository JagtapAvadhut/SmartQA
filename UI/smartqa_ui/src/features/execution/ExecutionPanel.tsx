import { useState } from 'react'
import type { ExecutionRun, ProgressEvent } from '../../types/intent'
import { eventLabel } from '../workspace/eventLabels'
import { EventDetailModal } from './EventDetailModal'

interface ExecutionPanelProps {
  run: ExecutionRun | null
  events: ProgressEvent[]
  onStop?: () => void
  stopping?: boolean
}

function parseCandidates(message: string | null): string[] {
  if (!message) return []
  const match = message.match(/Multiple matching elements for '[^']+': (.+)$/i)
  if (!match) return []
  return match[1].split(',').map((item) => item.trim()).filter(Boolean)
}

export function ExecutionPanel({ run, events, onStop, stopping }: ExecutionPanelProps) {
  const [details, setDetails] = useState(false)
  const [selectedEvent, setSelectedEvent] = useState<ProgressEvent | null>(null)
  if (!run && events.length === 0) {
    return (
      <article className="card">
        <h2>Execution result</h2>
        <p className="muted">Generate a valid test before execution.</p>
      </article>
    )
  }
  const passed = run?.status === 'PASSED' || run?.exitCode === 0
  const failed = run?.status === 'FAILED' || run?.status === 'ERROR' || (run?.exitCode != null && run.exitCode !== 0)
  const stopped = run?.status === 'STOPPED'
  const candidates = parseCandidates(run?.errorMessage ?? null)
  const duration = run?.durationMs != null ? `${(run.durationMs / 1000).toFixed(1)} seconds` : '—'
  return (
    <article className="card">
      <h2>Execution result</h2>
      <p>
        Status:{' '}
        <span className={passed ? 'ok-text' : failed ? 'error-text' : stopped ? 'muted' : 'muted'}>
          {passed ? '✓ PASSED' : failed ? '✗ FAILED' : stopped ? '■ STOPPED' : run?.status ?? 'RUNNING'}
        </span>
      </p>
      <p className="muted">Duration: {duration}</p>
      <p className="muted">Browser: Chromium</p>
      <p className="muted">Provider: PLAYWRIGHT_JAVA</p>
      {run?.status === 'RUNNING' && onStop ? (
        <div className="toolbar">
          <button className="btn" type="button" onClick={onStop} disabled={stopping}>
            {stopping ? 'Stopping…' : 'Stop Test'}
          </button>
        </div>
      ) : null}
      <ol className="progress-list">
        {events.map((event, index) => (
          <li
            key={`${event.type}-${index}`}
            className={event.type.includes('FAIL') ? 'failed' : event.type.includes('STOP') ? '' : 'done'}
            style={{ cursor: 'pointer' }}
            onClick={() => setSelectedEvent(event)}
          >
            <span>{event.type.includes('FAIL') ? '✗' : event.type.includes('STOP') ? '■' : '✓'}</span>{' '}
            {eventLabel(event.type, event.message)}
            {event.stepNumber ? <span className="muted"> (step {event.stepNumber})</span> : null}
          </li>
        ))}
      </ol>
      {failed ? (
        <div className="failure-box">
          <h3>A step did not complete</h3>
          <p>{run?.errorMessage || 'SmartQA could not finish this test.'}</p>
          {candidates.length ? (
            <>
              <p>SmartQA found {candidates.length} possible elements.</p>
              <ol className="step-list">
                {candidates.map((candidate, index) => (
                  <li key={candidate}>Candidate {index + 1}: {candidate}</li>
                ))}
              </ol>
            </>
          ) : null}
        </div>
      ) : null}
      <button className="btn" type="button" onClick={() => setDetails((value) => !value)}>
        {details ? 'Hide details' : 'View details'}
      </button>
      {details ? (
        <div>
          {run?.errorMessage ? <pre className="log-block">{run.errorMessage}</pre> : null}
          {run?.stderr ? <pre className="log-block">{run.stderr}</pre> : null}
          {run?.stdout ? <pre className="log-block">{run.stdout}</pre> : null}
        </div>
      ) : null}
      {selectedEvent ? <EventDetailModal event={selectedEvent} onClose={() => setSelectedEvent(null)} /> : null}
    </article>
  )
}
