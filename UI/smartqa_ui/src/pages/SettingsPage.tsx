import { useEffect, useState } from 'react'
import { getAiHealth, getAiSettings } from '../api/settings'
import { ApiError } from '../api/client'
import type { AiHealthSnapshot, AiSettings } from '../types/intent'
import { PageHeader } from '../ui/PageHeader'
import { StatusDot } from '../ui/StatusDot'

export function SettingsPage() {
  const [settings, setSettings] = useState<AiSettings | null>(null)
  const [health, setHealth] = useState<AiHealthSnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showAdvanced, setShowAdvanced] = useState(false)

  useEffect(() => {
    Promise.all([getAiSettings(), getAiHealth()])
      .then(([nextSettings, nextHealth]) => {
        setSettings(nextSettings)
        setHealth(nextHealth)
      })
      .catch((err: unknown) => {
        setError(err instanceof ApiError ? 'Unable to load settings. Is the SmartQA backend running?' : 'Unable to load settings')
      })
  }, [])

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        kicker="Configuration"
        title="Settings"
        description="Read-only status from the backend. API keys never appear in the browser."
      />
      {error ? <p className="error-text mb-4">{error}</p> : null}

      <div className="grid gap-4">
        <article className="card">
          <h2 className="mb-4 text-base font-semibold">AI providers</h2>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Primary</dt>
              <dd className="mt-1 font-medium">{settings?.primaryProvider ?? settings?.provider ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Fallback</dt>
              <dd className="mt-1 font-medium">{settings?.fallbackProvider || 'None'}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Model</dt>
              <dd className="mt-1 font-medium">
                {(settings?.primaryProvider ?? settings?.provider) === 'gemini'
                  ? settings?.geminiModel ?? '—'
                  : settings?.ollamaModel ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Gemini API key</dt>
              <dd className="mt-1 font-medium">{settings?.geminiConfigured ? 'Configured' : 'Not configured'}</dd>
            </div>
          </dl>
          <ul className="mt-5 grid gap-2">
            {(health?.providers ?? []).map((item) => {
              const usable = item.status === 'AVAILABLE' || item.status === 'COLD'
              return (
                <li key={item.provider} className="flex items-center gap-2 rounded-lg border border-border bg-bg/40 px-3 py-2 text-sm">
                  <StatusDot ok={usable} />
                  <span>
                    {item.provider === 'gemini' ? 'Gemini' : item.provider === 'ollama' ? 'Ollama' : item.provider}
                    {' — '}
                    {usable ? 'Ready' : 'Unavailable'}
                    {item.provider === 'gemini' && (item.configuredKeys ?? 0) > 0
                      ? ` · ${item.healthyKeys ?? 0}/${item.configuredKeys} keys`
                      : ''}
                  </span>
                </li>
              )
            })}
            {(health?.providers ?? []).length === 0 ? <li className="text-sm text-muted">Checking…</li> : null}
          </ul>
        </article>

        <article className="card">
          <h2 className="mb-4 text-base font-semibold">Browser</h2>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Engine</dt>
              <dd className="mt-1 font-medium">Chromium (Playwright)</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Default mode</dt>
              <dd className="mt-1 font-medium">Headed (visible). Change per run in Workspace advanced options.</dd>
            </div>
          </dl>
        </article>

        <article className="card">
          <button className="btn" type="button" onClick={() => setShowAdvanced((v) => !v)}>
            {showAdvanced ? 'Hide advanced' : 'Show advanced'}
          </button>
          {showAdvanced ? (
            <dl className="mt-4 grid gap-4 sm:grid-cols-2">
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted">AI timeout</dt>
                <dd className="mt-1">{settings?.timeoutSeconds ?? '—'} seconds</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted">Ollama base URL</dt>
                <dd className="mt-1">{settings?.ollamaBaseUrl ?? '—'}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted">Ollama model</dt>
                <dd className="mt-1">{settings?.ollamaModel ?? '—'}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted">Gemini model</dt>
                <dd className="mt-1">{settings?.geminiModel ?? '—'}</dd>
              </div>
            </dl>
          ) : null}
        </article>
      </div>
    </section>
  )
}
