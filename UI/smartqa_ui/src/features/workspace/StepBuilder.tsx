import { useMemo } from 'react'

export const STEP_ACTIONS = [
  'NAVIGATE',
  'CLICK',
  'INPUT',
  'SELECT',
  'CHECKBOX',
  'PRESS_KEY',
  'HOVER',
  'WAIT',
  'VERIFY',
  'SEARCH',
  'FILTER',
] as const

export const STEP_LOCATIONS = [
  'AUTO',
  'TOP_LEFT',
  'TOP_CENTER',
  'TOP_RIGHT',
  'MIDDLE_LEFT',
  'CENTER',
  'MIDDLE_RIGHT',
  'BOTTOM_LEFT',
  'BOTTOM_CENTER',
  'BOTTOM_RIGHT',
  'HEADER',
  'SIDEBAR_LEFT',
  'SIDEBAR_RIGHT',
  'CONTENT',
  'FOOTER',
  'MODAL',
  'DIALOG',
] as const

export type StepAction = (typeof STEP_ACTIONS)[number]
export type StepLocation = (typeof STEP_LOCATIONS)[number]

export interface StructuredStep {
  id: string
  order: number
  action: StepAction
  target: string
  value: string
  assertion: string
  location: StepLocation
}

export function createEmptyStep(order: number): StructuredStep {
  return {
    id: `step_${crypto.randomUUID().slice(0, 8)}`,
    order,
    action: 'CLICK',
    target: '',
    value: '',
    assertion: '',
    location: 'AUTO',
  }
}

export function stepsToNaturalLanguage(steps: StructuredStep[]): string {
  return steps
    .map((step, index) => {
      const bits = [`${index + 1}. ${step.action}`]
      if (step.location && step.location !== 'AUTO') bits.push(`[${step.location}]`)
      if (step.target.trim()) bits.push(step.target.trim())
      if (step.value.trim()) bits.push(`= ${step.value.trim()}`)
      if (step.assertion.trim()) bits.push(`(${step.assertion.trim()})`)
      return bits.join(' ')
    })
    .join('\n')
}

function looksLikeHttpUrl(value: string): boolean {
  const normalized = (value || '').trim().toLowerCase()
  return normalized.startsWith('http://') || normalized.startsWith('https://')
}

function coerceUrlNavigation(step: StructuredStep): StructuredStep {
  const url = looksLikeHttpUrl(step.target)
    ? step.target.trim()
    : looksLikeHttpUrl(step.value)
      ? step.value.trim()
      : ''
  if (!url) return step
  if (step.action === 'NAVIGATE') {
    return looksLikeHttpUrl(step.target) ? step : { ...step, target: url }
  }
  if (step.action === 'CLICK' || step.action === 'HOVER') {
    return { ...step, action: 'NAVIGATE', target: url }
  }
  return step
}

export function parseNaturalLanguageToSteps(text: string): StructuredStep[] {
  const lines = text
    .replaceAll('\r\n', '\n')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  if (lines.length === 0) return [createEmptyStep(1)]
  return lines.map((line, index) => {
    const cleaned = line.replace(/^\d+[\).:\-]\s*/, '')
    const actionMatch = cleaned.match(
      /^(navigate|open|visit|go(?:\s+to)?|click|input|select|checkbox|press_key|hover|wait|verify|search|filter)\b/i,
    )
    const rawAction = (actionMatch?.[1]?.toUpperCase() ?? 'CLICK').replace(/\s+/g, '_')
    let action: StepAction = (STEP_ACTIONS as readonly string[]).includes(rawAction)
      ? (rawAction as StepAction)
      : 'CLICK'
    if (rawAction === 'OPEN' || rawAction === 'VISIT' || rawAction === 'GO' || rawAction === 'GO_TO') {
      action = 'NAVIGATE'
    }
    let rest = actionMatch ? cleaned.slice(actionMatch[0].length).trim() : cleaned
    let location: StepLocation = 'AUTO'
    const locMatch = rest.match(/^\[([A-Z_]+)\]\s*/i)
    if (locMatch) {
      const candidate = locMatch[1].toUpperCase() as StepLocation
      if ((STEP_LOCATIONS as readonly string[]).includes(candidate)) location = candidate
      rest = rest.slice(locMatch[0].length).trim()
    }
    let value = ''
    let assertion = ''
    const assertionMatch = rest.match(/\(([^)]+)\)\s*$/)
    if (assertionMatch) {
      assertion = assertionMatch[1].trim()
      rest = rest.slice(0, assertionMatch.index).trim()
    }
    const valueMatch = rest.match(/=\s*(.+)$/)
    if (valueMatch) {
      value = valueMatch[1].trim()
      rest = rest.slice(0, valueMatch.index).trim()
    }
    const parsed: StructuredStep = {
      id: `step_${crypto.randomUUID().slice(0, 8)}`,
      order: index + 1,
      action: (STEP_ACTIONS as readonly string[]).includes(action) ? action : 'CLICK',
      target: rest,
      value,
      assertion,
      location,
    }
    if (!actionMatch && looksLikeHttpUrl(cleaned)) {
      parsed.action = 'NAVIGATE'
      parsed.target = cleaned
    }
    return coerceUrlNavigation(parsed)
  })
}

interface StepBuilderProps {
  steps: StructuredStep[]
  onChange: (steps: StructuredStep[]) => void
  disabled?: boolean
}

export function StepBuilder({ steps, onChange, disabled }: StepBuilderProps) {
  const ordered = useMemo(
    () => [...steps].sort((a, b) => a.order - b.order),
    [steps],
  )

  const update = (id: string, patch: Partial<StructuredStep>) => {
    onChange(ordered.map((step) => (step.id === id ? coerceUrlNavigation({ ...step, ...patch }) : step)))
  }

  const addStep = () => {
    onChange([...ordered, createEmptyStep(ordered.length + 1)])
  }

  const removeStep = (id: string) => {
    const next = ordered.filter((step) => step.id !== id).map((step, index) => ({ ...step, order: index + 1 }))
    onChange(next.length ? next : [createEmptyStep(1)])
  }

  const move = (id: string, direction: -1 | 1) => {
    const index = ordered.findIndex((step) => step.id === id)
    const swapWith = index + direction
    if (index < 0 || swapWith < 0 || swapWith >= ordered.length) return
    const copy = [...ordered]
    const tmp = copy[index]
    copy[index] = copy[swapWith]
    copy[swapWith] = tmp
    onChange(copy.map((step, i) => ({ ...step, order: i + 1 })))
  }

  return (
    <div className="step-builder">
      {ordered.map((step, index) => (
        <div className="step-row" key={step.id}>
          <div className="step-row-header">
            <strong>Step {index + 1}</strong>
            <div className="step-row-actions">
              <button type="button" className="btn" disabled={disabled || index === 0} onClick={() => move(step.id, -1)}>
                ↑
              </button>
              <button
                type="button"
                className="btn"
                disabled={disabled || index === ordered.length - 1}
                onClick={() => move(step.id, 1)}
              >
                ↓
              </button>
              <button type="button" className="btn" disabled={disabled || ordered.length <= 1} onClick={() => removeStep(step.id)}>
                Remove
              </button>
            </div>
          </div>
          <div className="step-fields">
            <label>
              Location
              <select
                value={step.location}
                disabled={disabled}
                onChange={(e) => update(step.id, { location: e.target.value as StepLocation })}
              >
                {STEP_LOCATIONS.map((loc) => (
                  <option key={loc} value={loc}>
                    {loc}
                  </option>
                ))}
              </select>
              <span className="muted" style={{ fontSize: 11 }}>
                Search hint, not a fixed click position
              </span>
            </label>
            <label>
              Action
              <select
                value={step.action}
                disabled={disabled}
                onChange={(e) => update(step.id, { action: e.target.value as StepAction })}
              >
                {STEP_ACTIONS.map((action) => (
                  <option key={action} value={action}>
                    {action}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Target
              <input
                value={step.target}
                disabled={disabled}
                placeholder="profile icon"
                onChange={(e) => update(step.id, { target: e.target.value })}
              />
            </label>
            <label>
              Value
              <input
                value={step.value}
                disabled={disabled}
                placeholder="optional"
                onChange={(e) => update(step.id, { value: e.target.value })}
              />
            </label>
            <label>
              Assertion
              <input
                value={step.assertion}
                disabled={disabled}
                placeholder="optional"
                onChange={(e) => update(step.id, { assertion: e.target.value })}
              />
            </label>
          </div>
        </div>
      ))}
      <button type="button" className="btn primary" disabled={disabled} onClick={addStep}>
        + Add Step
      </button>
    </div>
  )
}
