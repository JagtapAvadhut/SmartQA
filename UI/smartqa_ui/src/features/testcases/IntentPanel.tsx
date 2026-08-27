import type { IntentContract } from '../../types/intent'
import { filterRows } from '../../types/intent'

interface IntentPanelProps {
  intent: IntentContract | null
  onClarify: (questionId: string, option: string) => void
}

function humanStep(action: string, target: string | null, value: string | null, assertion: string | null): string {
  const focus = [target, value].filter(Boolean).join(' → ')
  switch (action) {
    case 'navigate':
      return `Navigate to ${focus || 'the application'}`
    case 'input':
      return `Enter ${value ?? ''} in ${target ?? 'field'}`
    case 'click':
      return `Click ${target ?? 'element'}`
    case 'select':
      return `Select ${value ?? ''} in ${target ?? 'list'}`
    case 'checkbox':
      return `Set ${target ?? 'checkbox'} to ${value ?? 'checked'}`
    case 'verify':
      return `Verify ${assertion || target || value || 'result'}`
    case 'filter':
      return `Apply filter ${focus}`
    case 'press_key':
      return `Press ${value ?? target ?? 'key'}`
    case 'wait':
      return `Wait ${value ?? target ?? ''}`
    default:
      return `${action} ${focus}`.trim()
  }
}

export function IntentPanel({ intent, onClarify }: IntentPanelProps) {
  if (!intent) {
    return (
      <article className="card">
        <h2>Test understanding</h2>
        <p className="muted">Analyze the test to see the scenario, steps, and assertions.</p>
      </article>
    )
  }
  const filters = filterRows(intent)
  const steps = intent.scenarios?.flatMap((scenario) => scenario.steps) ?? []
  const assertions = steps.filter((step) => step.action === 'verify' || step.assertion)
  const confidence = intent.confidence == null ? '—' : `${Math.round(intent.confidence * 100)}%`
  return (
    <article className="card">
      <h2>Test understanding</h2>
      <p>
        Status: <span className="badge">{intent.status}</span>
      </p>
      <dl className="kv compact-kv">
        <div>
          <dt>Scenario</dt>
          <dd>{intent.testName || intent.scenarios?.[0]?.name || '—'}</dd>
        </div>
        <div>
          <dt>Actions</dt>
          <dd>{steps.length}</dd>
        </div>
        <div>
          <dt>Assertions</dt>
          <dd>{assertions.length}</dd>
        </div>
        <div>
          <dt>Confidence</dt>
          <dd>{confidence}</dd>
        </div>
      </dl>
      {filters.length ? (
        <div className="scenario-block">
          <h3>Filters</h3>
          <dl className="kv">
            {filters.map((filter) => (
              <div key={`${filter.field}-${filter.value}`}>
                <dt>{filter.field}</dt>
                <dd>{filter.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      ) : (
        <p className="muted">Filters: none</p>
      )}
      {intent.clarifications?.length ? (
        <div className="scenario-block">
          <h3>Clarifications</h3>
          {intent.clarifications.map((question) => (
            <div key={question.id}>
              <p>{question.question}</p>
              <div className="toolbar">
                {(question.options ?? []).map((option) => (
                  <button
                    key={option}
                    className="btn"
                    type="button"
                    onClick={() => onClarify(question.id, option)}
                  >
                    {option}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : null}
      {intent.scenarios?.map((scenario) => (
        <div key={scenario.id} className="scenario-block">
          <h3>Steps</h3>
          <ol className="step-list">
            {scenario.steps.map((step) => (
              <li key={step.id}>{humanStep(step.action, step.target, step.value, step.assertion)}</li>
            ))}
          </ol>
        </div>
      ))}
      {assertions.length ? (
        <div className="scenario-block">
          <h3>Assertions</h3>
          <ul className="step-list">
            {assertions.map((step) => (
              <li key={step.id}>{step.assertion || step.value || step.target}</li>
            ))}
          </ul>
        </div>
      ) : null}
    </article>
  )
}
