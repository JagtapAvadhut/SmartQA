import type { ProgressStepStatus } from './eventLabels'

interface PipelineTimelineProps {
  steps: { key: string; label: string; status: ProgressStepStatus }[]
  currentLabel?: string | null
}

export function PipelineTimeline({ steps, currentLabel }: PipelineTimelineProps) {
  return (
    <div className="pipeline-timeline">
      {currentLabel ? (
        <p className="pipeline-current muted">
          Now: <strong>{currentLabel}</strong>
        </p>
      ) : null}
      <ol className="pipeline-steps">
        {steps.map((step) => (
          <li key={step.key} className={`pipeline-step pipeline-step-${step.status}`}>
            <span className="pipeline-marker" aria-hidden>
              {step.status === 'completed' ? '✓' : step.status === 'failed' ? '✗' : step.status === 'running' ? '●' : '○'}
            </span>
            <span>{step.label}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}
