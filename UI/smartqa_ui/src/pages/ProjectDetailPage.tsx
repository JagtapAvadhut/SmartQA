import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getProject } from '../api/projects'
import { createTestCase, listTestCases } from '../api/testcases'
import { ApiError } from '../api/client'
import type { Project } from '../types/project'
import type { TestCase, TestCaseRequest } from '../types/testcase'

const emptyForm: TestCaseRequest = {
  name: '',
  description: '',
  naturalLanguage: 'Open https://example.com\nClick More information\nVerify that the heading is visible',
}

export function ProjectDetailPage() {
  const { projectId } = useParams()
  const navigate = useNavigate()
  const [project, setProject] = useState<Project | null>(null)
  const [testCases, setTestCases] = useState<TestCase[]>([])
  const [form, setForm] = useState<TestCaseRequest>(emptyForm)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh() {
    if (!projectId) {
      return
    }
    setLoading(true)
    try {
      const [loadedProject, loadedCases] = await Promise.all([
        getProject(projectId),
        listTestCases(projectId),
      ])
      setProject(loadedProject)
      setTestCases(loadedCases)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to load project')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [projectId])

  async function onCreate(event: FormEvent) {
    event.preventDefault()
    if (!projectId) {
      return
    }
    try {
      const created = await createTestCase(projectId, form)
      setForm(emptyForm)
      navigate(`/projects/${projectId}/test-cases/${created.id}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to create test case')
    }
  }

  return (
    <section className="page">
      <header className="page-header">
        <div>
          <p className="muted">
            <Link to="/projects">Projects</Link>
          </p>
          <h1>{project?.name ?? 'Project'}</h1>
          <p className="muted">{project?.applicationUrl}</p>
        </div>
      </header>
      {error ? <p className="error-text">{error}</p> : null}
      <div className="split">
        <article className="card">
          <h2>Create test case</h2>
          <form className="form-grid" onSubmit={onCreate}>
            <label>
              Name
              <input
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                required
              />
            </label>
            <label>
              Description
              <input
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
              />
            </label>
            <label>
              Natural-language steps
              <textarea
                className="editor"
                rows={10}
                value={form.naturalLanguage}
                onChange={(event) => setForm({ ...form, naturalLanguage: event.target.value })}
                required
              />
            </label>
            <div>
              <button className="btn primary" type="submit">
                Create
              </button>
            </div>
          </form>
        </article>
        <article className="card">
          <h2>Test cases</h2>
          <p className="muted">Environment: {project?.environment ?? '—'}</p>
          {loading ? <p className="muted">Loading…</p> : null}
          {!loading && testCases.length === 0 ? <p className="muted">No test cases yet.</p> : null}
          {testCases.length > 0 ? (
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Status</th>
                  <th>Steps</th>
                </tr>
              </thead>
              <tbody>
                {testCases.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <Link to={`/projects/${projectId}/test-cases/${item.id}`}>{item.name}</Link>
                    </td>
                    <td>
                      <span className="badge">{item.status}</span>
                    </td>
                    <td>
                      {item.scenarios.reduce((count, scenario) => count + scenario.steps.length, 0)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
        </article>
      </div>
    </section>
  )
}
