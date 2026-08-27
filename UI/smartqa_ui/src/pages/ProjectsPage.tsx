import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { createProject, deleteProject, listProjects, updateProject } from '../api/projects'
import { ApiError } from '../api/client'
import type { Project, ProjectRequest } from '../types/project'
import { PageHeader } from '../ui/PageHeader'
import { EmptyState } from '../ui/EmptyState'

const emptyForm: ProjectRequest = {
  name: '',
  description: '',
  applicationUrl: '',
  environment: 'local',
}

export function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [form, setForm] = useState<ProjectRequest>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh() {
    setLoading(true)
    try {
      setProjects(await listProjects())
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to load projects')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    try {
      if (editingId) {
        await updateProject(editingId, form)
      } else {
        await createProject(form)
      }
      setForm(emptyForm)
      setEditingId(null)
      await refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to save project')
    }
  }

  async function onDelete(id: string) {
    if (!window.confirm('Delete this project?')) {
      return
    }
    await deleteProject(id)
    await refresh()
  }

  function startEdit(project: Project) {
    setEditingId(project.id)
    setForm({
      name: project.name,
      description: project.description ?? '',
      applicationUrl: project.applicationUrl,
      environment: project.environment ?? '',
    })
  }

  return (
    <section className="mx-auto max-w-5xl">
      <PageHeader
        kicker="Library"
        title="Projects"
        description="Each project is an application you want SmartQA to test."
      />
      {error ? <p className="error-text mb-4">{error}</p> : null}
      <div className="grid gap-5 lg:grid-cols-[minmax(280px,0.9fr)_1.1fr]">
        <article className="card">
          <h2 className="mb-4 text-base font-semibold">{editingId ? 'Edit project' : 'New project'}</h2>
          <form className="grid gap-3" onSubmit={onSubmit}>
            <label className="grid gap-1 text-sm text-muted">
              Project name
              <input
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                required
              />
            </label>
            <label className="grid gap-1 text-sm text-muted">
              Application URL
              <input
                value={form.applicationUrl}
                onChange={(event) => setForm({ ...form, applicationUrl: event.target.value })}
                placeholder="https://example.com"
                required
              />
            </label>
            <label className="grid gap-1 text-sm text-muted">
              Environment
              <input
                value={form.environment}
                onChange={(event) => setForm({ ...form, environment: event.target.value })}
              />
            </label>
            <label className="grid gap-1 text-sm text-muted">
              Description
              <textarea
                rows={3}
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
              />
            </label>
            <div className="flex gap-2 pt-1">
              <button className="btn primary" type="submit">
                {editingId ? 'Save' : 'Create'}
              </button>
              {editingId ? (
                <button
                  className="btn"
                  type="button"
                  onClick={() => {
                    setEditingId(null)
                    setForm(emptyForm)
                  }}
                >
                  Cancel
                </button>
              ) : null}
            </div>
          </form>
        </article>
        <article className="card">
          <h2 className="mb-4 text-base font-semibold">Existing</h2>
          {loading ? <p className="text-sm text-muted">Loading…</p> : null}
          {!loading && projects.length === 0 ? (
            <EmptyState title="No projects yet" detail="Create a project for the application you want to test." />
          ) : (
            <div className="grid gap-3">
              {projects.map((project) => (
                <div key={project.id} className="rounded-xl border border-border bg-bg/40 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <Link className="font-semibold hover:text-accent" to={`/projects/${project.id}`}>
                        {project.name}
                      </Link>
                      <p className="mt-1 text-sm text-muted">{project.applicationUrl}</p>
                      <p className="mt-1 text-xs text-muted">
                        {project.environment ?? 'local'} · {project.testCaseCount} tests
                      </p>
                    </div>
                    <div className="flex gap-2">
                      <button className="btn" type="button" onClick={() => startEdit(project)}>
                        Edit
                      </button>
                      <button className="btn" type="button" onClick={() => void onDelete(project.id)}>
                        Delete
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </article>
      </div>
    </section>
  )
}
