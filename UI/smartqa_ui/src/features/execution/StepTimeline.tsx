import type { ProgressEvent, ExecutionStepStatus } from '../../types/intent'

interface StepInfo {
  stepNumber: number
  action: string
  target: string
  status: ExecutionStepStatus
}

interface StepTimelineProps {
  events: ProgressEvent[]
}

function buildSteps(events: ProgressEvent[]): StepInfo[] {
  const steps: Map<number, StepInfo> = new Map()
  let totalSteps = 0
  for (const e of events) {
    if (e.totalSteps && e.totalSteps > totalSteps) totalSteps = e.totalSteps
    const n = e.stepNumber
    if (!n || n < 1) continue
    if (!steps.has(n)) {
      steps.set(n, {
        stepNumber: n,
        action: (e.details?.action as string) || e.message || '',
        target: (e.details?.stepId as string) || '',
        status: 'PENDING',
      })
    }
    const step = steps.get(n)!
    if (e.type === 'STEP_STARTED' || e.type === 'ACTION_STARTED') step.status = 'RUNNING'
    if (e.type === 'STEP_COMPLETED' || e.type === 'ACTION_COMPLETED') step.status = 'PASSED'
    if (e.type === 'ACTION_FAILED' || e.type === 'STEP_FAILED') step.status = 'FAILED'
    if (e.type === 'EXECUTION_STOPPED') step.status = 'STOPPED'
    if (e.details?.action) step.action = e.details.action as string
  }
  for (let i = 1; i <= totalSteps; i++) {
    if (!steps.has(i)) {
      steps.set(i, { stepNumber: i, action: '', target: '', status: 'PENDING' })
    }
  }
  return Array.from(steps.values()).sort((a, b) => a.stepNumber - b.stepNumber)
}

const STATUS_ICON: Record<ExecutionStepStatus, string> = {
  PENDING: '○',
  RUNNING: '●',
  PASSED: '✓',
  FAILED: '✗',
  STOPPED: '■',
  SKIPPED: '—',
}

const STATUS_CLASS: Record<ExecutionStepStatus, string> = {
  PENDING: 'muted',
  RUNNING: 'ok-text',
  PASSED: 'done',
  FAILED: 'error-text',
  STOPPED: 'muted',
  SKIPPED: 'muted',
}

export function StepTimeline({ events }: StepTimelineProps) {
  const steps = buildSteps(events)
  if (steps.length === 0) return null
  return (
    <article className="card">
      <h2>Step-by-step execution</h2>
      <ol className="progress-list">
        {steps.map((s) => (
          <li key={s.stepNumber} className={STATUS_CLASS[s.status]}>
            <span>{STATUS_ICON[s.status]}</span>{' '}
            Step {s.stepNumber}{s.action ? ` — ${s.action}` : ''}{' '}
            <span className={`badge ${s.status === 'FAILED' ? 'badge-error' : s.status === 'PASSED' ? 'badge-ok' : ''}`}>
              {s.status}
            </span>
          </li>
        ))}
      </ol>
    </article>
  )
}
