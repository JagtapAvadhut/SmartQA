import { NavLink } from 'react-router-dom'
import { useEffect, useState, type ReactNode } from 'react'
import type { HealthCheckResult } from '../api/health'
import { getAiHealth } from '../api/settings'
import type { AiHealthSnapshot } from '../types/intent'
import { StatusDot } from '../ui/StatusDot'
import { Button } from '../ui/Button'

interface LayoutProps {
  children: ReactNode
  health: HealthCheckResult | null
  onRetryHealth: () => void
}

function providerLine(health: AiHealthSnapshot | null, id: string): { ok: boolean; label: string } {
  const item = health?.providers?.find((p) => p.provider.toLowerCase() === id)
  if (!item) {
    return { ok: false, label: `${id === 'gemini' ? 'Gemini' : 'Ollama'} unknown` }
  }
  const usable = item.status === 'AVAILABLE' || item.status === 'COLD'
  const name = id === 'gemini' ? 'Gemini' : id === 'ollama' ? 'Ollama' : item.provider
  if (usable) {
    const keyHint =
      id === 'gemini' && (item.configuredKeys ?? 0) > 0
        ? ` · ${item.healthyKeys ?? 0}/${item.configuredKeys} keys`
        : ''
    return { ok: true, label: `${name} Ready${keyHint}` }
  }
  return { ok: false, label: `${name} unavailable` }
}

const navClass = ({ isActive }: { isActive: boolean }) =>
  `flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
    isActive ? 'bg-accent/15 text-text shadow-[inset_0_0_0_1px_rgba(110,168,255,0.25)]' : 'text-muted hover:bg-white/5 hover:text-text'
  }`

function Icon({ d }: { d: string }) {
  return (
    <svg className="h-4 w-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d={d} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function Layout({ children, health, onRetryHealth }: LayoutProps) {
  const checking = health == null
  const connected = health?.ok === true
  const [aiHealth, setAiHealth] = useState<AiHealthSnapshot | null>(null)
  const [showTechHealth, setShowTechHealth] = useState(false)

  useEffect(() => {
    if (!connected) {
      setAiHealth(null)
      return
    }
    getAiHealth().then(setAiHealth).catch(() => setAiHealth(null))
  }, [connected])

  const gemini = providerLine(aiHealth, 'gemini')
  const ollama = providerLine(aiHealth, 'ollama')

  return (
    <div className="grid min-h-screen grid-cols-[248px_1fr] bg-bg text-text">
      <aside className="flex flex-col gap-6 border-r border-border bg-[#0e1628]/90 p-5 backdrop-blur">
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-accent to-[#3b6fd4] text-sm font-bold tracking-wide text-[#071018] shadow-[0_8px_20px_rgba(110,168,255,0.25)]">
            SQ
          </span>
          <div>
            <strong className="text-[15px]">SmartQA</strong>
            <div className="text-xs text-muted">Natural-language tests</div>
          </div>
        </div>
        <nav className="flex flex-col gap-1">
          <NavLink to="/" end className={navClass}>
            <Icon d="M4 7h16M4 12h10M4 17h16" />
            Workspace
          </NavLink>
          <NavLink to="/tests" className={navClass}>
            <Icon d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01" />
            Tests
          </NavLink>
          <NavLink to="/projects" className={navClass}>
            <Icon d="M4 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7z" />
            Projects
          </NavLink>
          <NavLink to="/settings" className={navClass}>
            <Icon d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a7.7 7.7 0 0 0 .1-1 7.7 7.7 0 0 0-.1-1l2-1.5-2-3.5-2.4 1a7 7 0 0 0-1.7-1L15 4h-4l-.4 2.5a7 7 0 0 0-1.7 1l-2.4-1-2 3.5 2 1.5a7.7 7.7 0 0 0-.1 1 7.7 7.7 0 0 0 .1 1L3.4 16.5l2 3.5 2.4-1a7 7 0 0 0 1.7 1L11 22h4l.4-2.5a7 7 0 0 0 1.7-1l2.4 1 2-3.5-2-1.5z" />
            Settings
          </NavLink>
        </nav>
        <div className="mt-auto space-y-3 rounded-xl border border-border bg-[#101a2e] p-3 text-sm">
          <div className="flex items-center gap-2">
            <StatusDot ok={connected} checking={checking} />
            <span>
              <strong>Backend</strong>
              <span className="text-muted">
                {' '}
                · {checking ? 'Checking…' : connected ? 'Connected' : 'Disconnected'}
              </span>
            </span>
          </div>
          {connected ? (
            <>
              <div className="flex items-center gap-2 text-muted">
                <StatusDot ok={gemini.ok} />
                <span>{gemini.label}</span>
              </div>
              <div className="flex items-center gap-2 text-muted">
                <StatusDot ok={ollama.ok} />
                <span>{ollama.label}</span>
              </div>
            </>
          ) : !checking ? (
            <p className="m-0 text-sm text-danger">Backend is not running.</p>
          ) : null}
          {!checking && !connected ? (
            <Button type="button" onClick={onRetryHealth}>
              Retry connection
            </Button>
          ) : null}
          <button
            className="text-left text-xs text-muted hover:text-text"
            type="button"
            onClick={() => setShowTechHealth((v) => !v)}
          >
            {showTechHealth ? 'Hide details' : 'Technical details'}
          </button>
          {showTechHealth ? (
            <div className="wrap text-[11px] text-muted">
              {health?.url ?? 'http://localhost:8081/api/health'}
              {health?.reason ? ` — ${health.reason}` : ''}
            </div>
          ) : null}
        </div>
      </aside>
      <main className="min-w-0 overflow-auto p-8">{children}</main>
    </div>
  )
}
