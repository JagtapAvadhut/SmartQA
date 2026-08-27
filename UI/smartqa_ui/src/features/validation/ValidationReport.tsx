import type { ValidationRun, ValidationResult } from '../../types/intent'
import { parseValidationResult } from '../../types/intent'

interface ValidationReportProps {
  run: ValidationRun
  testName?: string
  applicationUrl?: string
}

/** @deprecated Prefer OverviewPage pipeline result UX. Kept for advanced reuse. */
export function ValidationReport({ run, testName, applicationUrl }: ValidationReportProps) {
  const result: ValidationResult | null = parseValidationResult(run.result)
  if (!result) return null
  const passed = result.status === 'PASSED'
  return (
    <article className="card">
      <h2>Validation Report</h2>
      <dl className="kv compact-kv">
        <div><dt>Test</dt><dd>{testName || '—'}</dd></div>
        <div><dt>Result</dt><dd className={passed ? 'ok-text' : 'error-text'}>{passed ? '✓ PASS' : '✗ FAIL'}</dd></div>
        <div><dt>Browser</dt><dd>Chromium</dd></div>
        {applicationUrl ? <div><dt>URL</dt><dd className="wrap">{applicationUrl}</dd></div> : null}
        <div><dt>Steps</dt><dd>{result.totalSteps}</dd></div>
        <div><dt>Passed</dt><dd>{result.passedSteps}</dd></div>
        <div><dt>Failed</dt><dd>{result.failedSteps}</dd></div>
        <div><dt>Duration</dt><dd>{result.durationMs ? `${(result.durationMs / 1000).toFixed(1)}s` : '—'}</dd></div>
        <div><dt>Attempt</dt><dd>#{result.attemptNumber}</dd></div>
      </dl>
      {!passed && result.failedStepNumber ? (
        <div className="failure-box" style={{ marginTop: 12 }}>
          <h3>Failed Step</h3>
          <p>Step {result.failedStepNumber}{result.failedAction ? `: ${result.failedAction}` : ''}</p>
          {result.errorMessage ? <p>{result.errorMessage}</p> : null}
        </div>
      ) : null}
    </article>
  )
}
