import type { ValidationRun } from '../../types/intent'
import { parseValidationResult } from '../../types/intent'

interface ValidationHistoryProps {
  runs: ValidationRun[]
}

export function ValidationHistory({ runs }: ValidationHistoryProps) {
  if (runs.length === 0) return null
  return (
    <article className="card">
      <h2>Validation History</h2>
      <ul className="recent-list">
        {runs.map((run) => {
          const result = parseValidationResult(run.result)
          const passed = run.status === 'PASSED'
          const failed = run.status === 'FAILED'
          return (
            <li key={run.id} style={{ marginBottom: 8 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span className={passed ? 'ok-text' : failed ? 'error-text' : 'muted'}>
                  {passed ? '✓' : failed ? '✗' : '○'}
                </span>
                <strong>Attempt #{run.attemptNumber}</strong>
                <span className={`badge ${passed ? 'badge-ok' : failed ? 'badge-error' : ''}`}>
                  {run.status}
                </span>
                {run.durationMs ? <span className="muted">{(run.durationMs / 1000).toFixed(1)}s</span> : null}
              </div>
              {failed && result?.failedStepNumber ? (
                <p className="muted" style={{ marginLeft: 24, marginTop: 2 }}>
                  Step {result.failedStepNumber}{result.failedAction ? `: ${result.failedAction}` : ''}
                  {result.errorMessage ? ` — ${result.errorMessage}` : ''}
                </p>
              ) : null}
              {failed && !result?.failedStepNumber && run.errorMessage ? (
                <p className="muted" style={{ marginLeft: 24, marginTop: 2 }}>{run.errorMessage}</p>
              ) : null}
            </li>
          )
        })}
      </ul>
    </article>
  )
}
