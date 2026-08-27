import { useState } from 'react'
import type { ValidationRun, ValidationResult } from '../../types/intent'
import { parseValidationResult } from '../../types/intent'

interface ValidationPanelProps {
  validationRun: ValidationRun | null
  onValidate: () => void
  busy: boolean
  hasCode: boolean
}

/** @deprecated Prefer OverviewPage pipeline result UX. Kept for advanced reuse. */
export function ValidationPanel({ validationRun, onValidate, busy, hasCode }: ValidationPanelProps) {
  const [details, setDetails] = useState(false)
  const result: ValidationResult | null = parseValidationResult(validationRun?.result ?? null)
  const status = validationRun?.status ?? 'NOT_RUN'
  const passed = status === 'PASSED'
  const failed = status === 'FAILED'

  return (
    <article className="card">
      <h2>Generated Test Validation</h2>
      <p className="muted" style={{ fontSize: 13 }}>
        Independent verification — runs the actual generated test against the real application.
      </p>
      <p>
        Validation:{' '}
        <span className={passed ? 'ok-text' : failed ? 'error-text' : 'muted'}>
          {passed ? '✓ PASSED' : failed ? '✗ FAILED' : status === 'RUNNING' ? 'RUNNING…' : status === 'NOT_RUN' ? 'NOT RUN' : status}
        </span>
      </p>
      {result ? (
        <>
          <p className="muted">
            Steps: {result.passedSteps}/{result.totalSteps} passed
            {result.durationMs ? ` · ${(result.durationMs / 1000).toFixed(1)}s` : ''}
            {result.attemptNumber ? ` · Attempt #${result.attemptNumber}` : ''}
          </p>
          {result.steps.length > 0 ? (
            <ol className="progress-list">
              {result.steps.map((s) => (
                <li key={s.stepNumber} className={s.status === 'PASSED' ? 'done' : s.status === 'FAILED' ? 'failed' : ''}>
                  <span>{s.status === 'PASSED' ? '✓' : s.status === 'FAILED' ? '✗' : '○'}</span>{' '}
                  Step {s.stepNumber}{s.action ? ` — ${s.action}` : ''}{' '}
                  <span className={`badge ${s.status === 'FAILED' ? 'badge-error' : s.status === 'PASSED' ? 'badge-ok' : ''}`}>
                    {s.status}
                  </span>
                </li>
              ))}
            </ol>
          ) : null}
        </>
      ) : null}
      {failed && validationRun ? (
        <div className="failure-box">
          <h3>Validation Failed</h3>
          {result?.failedStepNumber ? (
            <p>Failed at step {result.failedStepNumber}{result.failedAction ? `: ${result.failedAction}` : ''}</p>
          ) : null}
          {(result?.errorMessage || validationRun.errorMessage) ? (
            <p>{result?.errorMessage || validationRun.errorMessage}</p>
          ) : null}
          <button className="btn" type="button" onClick={() => setDetails(v => !v)}>
            {details ? 'Hide details' : 'View details'}
          </button>
          {details ? (
            <div>
              {validationRun.stderr ? <pre className="log-block">{validationRun.stderr}</pre> : null}
              {validationRun.stdout ? <pre className="log-block">{validationRun.stdout}</pre> : null}
            </div>
          ) : null}
        </div>
      ) : null}
      <div className="toolbar" style={{ marginTop: 8 }}>
        <button className="btn primary" type="button" onClick={onValidate} disabled={busy || !hasCode}>
          {status === 'RUNNING' ? 'Validating…' : 'Validate Generated Test'}
        </button>
      </div>
    </article>
  )
}
