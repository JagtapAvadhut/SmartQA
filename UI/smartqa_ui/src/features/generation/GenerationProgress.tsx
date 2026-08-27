import type { ProgressEvent } from '../../types/intent'
import { ANALYZE_STEPS, eventLabel } from '../workspace/eventLabels'

interface GenerationProgressProps {
  events: ProgressEvent[]
  error: string | null
  phase: string
}

export function GenerationProgress({ events, error, phase }: GenerationProgressProps) {
  const types = new Set(events.map((event) => event.type))
  const latest = events.at(-1)
  const analyzing = phase === 'analyzing' || types.has('GENERATION_STARTED') || types.has('INTENT_ANALYZED')
  return (
    <article className="card">
      <h2>Live progress</h2>
      <ol className="progress-list">
        {ANALYZE_STEPS.map((step) => {
          const done =
            step.type === 'STEPS' || step.type === 'ASSERTIONS'
              ? types.has('INTENT_ANALYZED') || types.has('INTENT_READY') || phase === 'analysis-complete' || phase === 'generated' || phase === 'completed'
              : step.type === 'INTENT_ANALYZED'
                ? types.has('INTENT_ANALYZED') || types.has('INTENT_READY') || phase === 'analysis-complete' || phase === 'generated' || phase === 'completed'
                : types.has(step.type)
          const current = analyzing && !done && (step.type === 'GENERATION_STARTED' || latest?.type === step.type)
          return (
            <li key={step.type} className={done ? 'done' : current ? 'current' : ''}>
              <span>{done ? '✓' : current ? '●' : '○'}</span> {step.label}
            </li>
          )
        })}
      </ol>
      {events.length ? (
        <ol className="progress-list timeline">
          {events.slice(-12).map((event, index) => (
            <li key={`${event.type}-${index}`} className={event.type.includes('FAIL') || event.type.includes('ERROR') ? 'failed' : 'done'}>
              {eventLabel(event.type, event.message)}
            </li>
          ))}
        </ol>
      ) : (
        <p className="muted">{phase === 'analyzing' ? 'Analyzing test…' : 'Idle'}</p>
      )}
      {error ? <p className="error-text">{error}</p> : null}
    </article>
  )
}
