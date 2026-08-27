import type { ProgressEvent } from '../../types/intent'

interface CurrentActionProps {
  events: ProgressEvent[]
  onStop?: () => void
  stopping?: boolean
  elapsed?: number
}

export function CurrentAction({ events, onStop, stopping, elapsed }: CurrentActionProps) {
  const last = events.at(-1)
  if (!last) return null

  const isRunning = !['EXECUTION_COMPLETED', 'EXECUTION_FAILED', 'EXECUTION_STOPPED'].includes(last.type)
  if (!isRunning) return null

  const action = (last.details?.action as string) || last.message || last.type
  const target = (last.details?.target as string) || ''
  const stepNumber = last.stepNumber || ''
  const totalSteps = last.totalSteps || ''

  return (
    <article className="card" style={{ borderLeft: '4px solid var(--accent)' }}>
      <h2>Current action</h2>
      <p className="ok-text" style={{ fontWeight: 600 }}>Running</p>
      <dl className="kv compact-kv">
        <div>
          <dt>Step</dt>
          <dd>
            {stepNumber}
            {totalSteps ? ` of ${totalSteps}` : ''}
          </dd>
        </div>
        <div>
          <dt>Doing</dt>
          <dd>
            {action}
            {target ? ` “${target}”` : ''}
          </dd>
        </div>
        {last.details?.candidateId ? (
          <div>
            <dt>Candidate</dt>
            <dd><code>{String(last.details.candidateId)}</code></dd>
          </div>
        ) : null}
        {last.details?.confidence != null ? (
          <div>
            <dt>Confidence</dt>
            <dd>{(Number(last.details.confidence) * 100).toFixed(0)}%</dd>
          </div>
        ) : null}
        {last.details?.whySelected || last.details?.explanation ? (
          <div>
            <dt>Why selected</dt>
            <dd className="wrap">{String(last.details.whySelected || last.details.explanation)}</dd>
          </div>
        ) : null}
        {last.details?.safetyGate ? (
          <div>
            <dt>Safety Gate</dt>
            <dd>{String(last.details.safetyGate)}</dd>
          </div>
        ) : null}
        {elapsed != null ? (
          <div>
            <dt>Elapsed</dt>
            <dd>{(elapsed / 1000).toFixed(1)}s</dd>
          </div>
        ) : null}
      </dl>
      {onStop ? (
        <div className="toolbar" style={{ marginTop: 8 }}>
          <button className="btn" type="button" onClick={onStop} disabled={stopping}>
            {stopping ? 'Stopping…' : 'Stop Test'}
          </button>
        </div>
      ) : null}
    </article>
  )
}
