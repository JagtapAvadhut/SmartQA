import { useState } from 'react'
import type { LocatorMemoryEntry } from '../../types/intent'
import { formatLocator } from '../workspace/session'

interface LocatorMemoryPanelProps {
  entries: LocatorMemoryEntry[]
}

export function LocatorMemoryPanel({ entries }: LocatorMemoryPanelProps) {
  const [openId, setOpenId] = useState<string | null>(null)
  const discovered = entries.filter((entry) => entry.resolvedLocator)
  if (discovered.length === 0) {
    return (
      <article className="card">
        <h2>Locator evidence</h2>
        <p className="muted">Verified locators appear after Generate Test.</p>
      </article>
    )
  }
  return (
    <article className="card">
      <h2>Locator evidence</h2>
      <ol className="step-list">
        {discovered.map((entry) => {
          const alternatives = (entry.locatorCloud ?? '')
            .split(' | ')
            .map((item) => item.trim())
            .filter(Boolean)
          const open = openId === entry.stepId
          return (
            <li key={entry.stepId}>
              <div>
                <strong>{entry.semanticTarget || entry.action}</strong>
                <div className="muted wrap">{formatLocator(entry.locatorType, entry.resolvedLocator)}</div>
                <p className="muted">
                  Confidence: {Math.round((entry.confidence ?? 0) * 100)}% · {entry.healed ? 'Healed' : 'Verified'}
                </p>
                {alternatives.length > 1 ? (
                  <button
                    className="btn"
                    type="button"
                    onClick={() => setOpenId(open ? null : entry.stepId)}
                  >
                    {open ? 'Hide alternatives' : 'View alternatives'}
                  </button>
                ) : null}
                {open ? (
                  <ul className="step-list">
                    {alternatives.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : null}
              </div>
            </li>
          )
        })}
      </ol>
    </article>
  )
}
