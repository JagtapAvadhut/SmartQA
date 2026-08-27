import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listProjects } from '../api/projects'
import { listTestCases } from '../api/testcases'
import { ApiError } from '../api/client'
import { writeWorkspaceSession } from '../features/workspace/session'
import type { TestCase } from '../types/testcase'
import { PageHeader } from '../ui/PageHeader'
import { Badge } from '../ui/Badge'
import { EmptyState } from '../ui/EmptyState'

type StatusFilter = 'ALL' | 'PASSED' | 'FAILED' | 'DRAFT' | 'RUNNING'

interface TestRow {
  projectId: string
  projectName: string
  applicationUrl: string
  testCase: TestCase
}

function statusTone(status: string): 'ok' | 'danger' | 'accent' | 'muted' {
  if (status === 'PASSED') return 'ok'
  if (status === 'FAILED' || status === 'ANALYSIS_FAILED') return 'danger'
  if (status === 'RUNNING') return 'accent'
  return 'muted'
}

function statusLabel(status: string): string {
  switch (status) {
    case 'PASSED':
      return 'Passed'
    case 'FAILED':
      return 'Failed'
    case 'DRAFT':
      return 'Draft'
    case 'READY':
      return 'Ready'
    case 'RUNNING':
      return 'Running'
    case 'ANALYSIS_FAILED':
      return 'Needs attention'
    default:
      return status
  }
}

function formatWhen(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function TestsPage() {
  const navigate = useNavigate()
  const [rows, setRows] = useState<TestRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<StatusFilter>('ALL')

  useEffect(() => {
    void (async () => {
      setLoading(true)
      try {
        const projects = await listProjects()
        const groups = await Promise.all(
          projects.map(async (project) => {
            const cases = await listTestCases(project.id)
            return cases.map((testCase) => ({
              projectId: project.id,
              projectName: project.name,
              applicationUrl: project.applicationUrl,
              testCase,
            }))
          }),
        )
        setRows(
          groups
            .flat()
            .sort((a, b) => b.testCase.updatedAt.localeCompare(a.testCase.updatedAt)),
        )
        setError(null)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Unable to load tests')
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return rows.filter((row) => {
      if (filter === 'PASSED' && row.testCase.status !== 'PASSED') return false
      if (filter === 'FAILED' && row.testCase.status !== 'FAILED' && row.testCase.status !== 'ANALYSIS_FAILED') return false
      if (filter === 'DRAFT' && row.testCase.status !== 'DRAFT' && row.testCase.status !== 'READY') return false
      if (filter === 'RUNNING' && row.testCase.status !== 'RUNNING') return false
      if (!q) return true
      const hay = `${row.testCase.name} ${row.applicationUrl} ${row.projectName}`.toLowerCase()
      return hay.includes(q)
    })
  }, [rows, query, filter])

  function openTest(row: TestRow) {
    writeWorkspaceSession(row.projectId, row.testCase.id)
    void navigate(`/projects/${row.projectId}/test-cases/${row.testCase.id}`)
  }

  function runAgain(row: TestRow) {
    writeWorkspaceSession(row.projectId, row.testCase.id)
    void navigate('/', { state: { loadTestId: row.testCase.id, projectId: row.projectId } })
  }

  return (
    <section className="mx-auto max-w-5xl">
      <PageHeader
        kicker="Library"
        title="Tests"
        description="Recent automated tests across your applications."
        actions={
          <Link className="btn primary" to="/" state={{ resetWorkspace: true }}>
            New test
          </Link>
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <input
          className="min-w-[220px] flex-1"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search tests…"
          aria-label="Search tests"
        />
        <div className="flex flex-wrap gap-2">
          {(['ALL', 'PASSED', 'FAILED', 'DRAFT', 'RUNNING'] as StatusFilter[]).map((item) => (
            <button
              key={item}
              type="button"
              className={`rounded-full border px-3 py-1.5 text-xs font-medium ${
                filter === item ? 'border-accent bg-accent/15 text-text' : 'border-border text-muted'
              }`}
              onClick={() => setFilter(item)}
            >
              {item === 'ALL' ? 'All' : statusLabel(item)}
            </button>
          ))}
        </div>
      </div>

      {error ? <p className="error-text">{error}</p> : null}
      {loading ? <p className="text-sm text-muted">Loading tests…</p> : null}

      {!loading && filtered.length === 0 ? (
        <EmptyState title="No tests yet" detail="Create your first automated test from Workspace." />
      ) : (
        <div className="grid gap-3">
          {filtered.map((row) => (
            <article key={row.testCase.id} className="card flex flex-wrap items-center justify-between gap-4">
              <div className="min-w-0">
                <div className="mb-1 flex items-center gap-2">
                  <h2 className="m-0 text-base font-semibold">{row.testCase.name || 'Untitled test'}</h2>
                  <Badge tone={statusTone(row.testCase.status)}>{statusLabel(row.testCase.status)}</Badge>
                </div>
                <p className="m-0 text-sm text-muted">{applicationHost(row.applicationUrl)}</p>
                <p className="mt-1 text-xs text-muted">Last run {formatWhen(row.testCase.updatedAt)}</p>
              </div>
              <div className="flex gap-2">
                <button className="btn primary" type="button" onClick={() => openTest(row)}>
                  Open
                </button>
                <button className="btn" type="button" onClick={() => runAgain(row)}>
                  Run again
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function applicationHost(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}
