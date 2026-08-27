import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteTestCase, getTestCase, updateTestCase } from '../api/testcases'
import {
  clarifyTestCase,
  executeTestCase,
  startGeneration,
  getGenerationRun,
  getExecutionRun,
  saveGeneratedCode,
  stopExecutionRun,
  understandTestCase,
} from '../api/workflow'
import { connectSse } from '../api/sse'
import { ApiError } from '../api/client'
import { ExecutionPanel } from '../features/execution/ExecutionPanel'
import { ScreenshotTimeline } from '../features/execution/ScreenshotTimeline'
import { GenerationProgress } from '../features/generation/GenerationProgress'
import { LiveBrowserPanel } from '../features/generation/LiveBrowserPanel'
import { LocatorMemoryPanel } from '../features/generation/LocatorMemoryPanel'
import { IntentPanel } from '../features/testcases/IntentPanel'
import type { ExecutionRun, ProgressEvent } from '../types/intent'
import { parseIntent, parseLocatorMemory } from '../types/intent'
import { collapseRepeatedInstructions } from '../features/workspace/instructions'
import type { TestCase, TestCaseRequest } from '../types/testcase'

export function TestCasePage() {
  const { projectId, testCaseId } = useParams()
  const navigate = useNavigate()
  const [testCase, setTestCase] = useState<TestCase | null>(null)
  const [form, setForm] = useState<TestCaseRequest>({
    name: '',
    description: '',
    naturalLanguage: '',
  })
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [generationError, setGenerationError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [generationEvents, setGenerationEvents] = useState<ProgressEvent[]>([])
  const [executionEvents, setExecutionEvents] = useState<ProgressEvent[]>([])
  const [run, setRun] = useState<ExecutionRun | null>(null)
  const [generationRun, setGenerationRun] = useState<{ id: string } | null>(null)
  const genPollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const intent = useMemo(() => parseIntent(testCase?.intentContract ?? null), [testCase])
  const locatorMemory = useMemo(
    () => parseLocatorMemory(testCase?.locatorMemory ?? null),
    [testCase],
  )

  useEffect(() => {
    if (!testCaseId) {
      return
    }
    getTestCase(testCaseId)
      .then((loaded) => {
        setTestCase(loaded)
        setForm({
          name: loaded.name,
          description: loaded.description ?? '',
          naturalLanguage: loaded.naturalLanguage ?? '',
        })
        setCode(loaded.generatedCode ?? '')
        setError(null)
      })
      .catch((err: unknown) => {
        setError(err instanceof ApiError ? err.message : 'Unable to load test case')
      })
  }, [testCaseId])

  useEffect(() => {
    if (!testCaseId) {
      return
    }
    return connectSse(`/api/test-cases/${testCaseId}/generation/stream`, {
      streamKey: testCaseId,
      generationRunId: generationRun?.id ?? undefined,
      recoverState: async () => {
        if (!generationRun?.id || !testCaseId) return
        try {
          const updated = await getGenerationRun(generationRun.id)
          if (updated.status === 'COMPLETED') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            applyTestCase(await getTestCase(testCaseId))
          } else if (updated.status === 'FAILED' || updated.status === 'STOPPED') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            setGenerationError(updated.errorMessage || 'Generation failed')
          }
        } catch {
          // Polling remains fallback.
        }
      },
      handlers: {
        onEvent: (eventName, data) => {
          if (eventName === 'message') {
            return
          }
          setGenerationEvents((current) => [...current, data as ProgressEvent])
          if (eventName === 'GENERATION_COMPLETE' && testCaseId) {
            if (genPollRef.current) clearInterval(genPollRef.current)
            getTestCase(testCaseId).then(applyTestCase).catch(() => undefined)
          }
          if (eventName === 'GENERATION_ERROR') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            const evt = data as ProgressEvent
            setGenerationError(evt.message || 'Generation failed')
          }
        },
      },
    })
  }, [testCaseId, generationRun?.id])

  useEffect(() => {
    return () => {
      if (genPollRef.current) clearInterval(genPollRef.current)
    }
  }, [])

  useEffect(() => {
    if (!run?.id) {
      return
    }
    return connectSse(`/api/execution-runs/${run.id}/stream`, {
      streamKey: run.id,
      recoverState: async () => {
        try {
          setRun(await getExecutionRun(run.id))
        } catch {
          // Execution polling remains fallback.
        }
      },
      handlers: {
        onEvent: (eventName, data) => {
          if (eventName === 'message') {
            return
          }
          setExecutionEvents((current) => [...current, data as ProgressEvent])
        },
      },
    })
  }, [run?.id])

  async function onSave(event: FormEvent) {
    event.preventDefault()
    if (!testCaseId) {
      return
    }
    setBusy('save')
    try {
      const saved = await updateTestCase(testCaseId, {
        ...form,
        naturalLanguage: collapseRepeatedInstructions(form.naturalLanguage),
      })
      applyTestCase(saved)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to save test case')
    } finally {
      setBusy(null)
    }
  }

  async function onAnalyze() {
    if (!testCaseId) {
      return
    }
    setBusy('analyze')
    setError(null)
    try {
      const current = collapseRepeatedInstructions(form.naturalLanguage)
      const saved = await updateTestCase(testCaseId, {
        ...form,
        naturalLanguage: current,
      })
      applyTestCase(saved)
      applyTestCase(await understandTestCase(testCaseId))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to analyze test')
    } finally {
      setBusy(null)
    }
  }

  async function onClarify(questionId: string, option: string) {
    if (!testCaseId) {
      return
    }
    setBusy('clarify')
    try {
      applyTestCase(await clarifyTestCase(testCaseId, [{ questionId, selectedOption: option }]))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to apply clarification')
    } finally {
      setBusy(null)
    }
  }

  async function onGenerate() {
    if (!testCaseId) {
      return
    }
    setBusy('generate')
    setGenerationError(null)
    setGenerationEvents([])
    try {
      const started = await startGeneration(testCaseId)
      setGenerationRun(started)
      genPollRef.current = setInterval(async () => {
        try {
          const updated = await getGenerationRun(started.id)
          if (updated.status === 'COMPLETED' || updated.status === 'FAILED' || updated.status === 'STOPPED') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            if (updated.status === 'COMPLETED') {
              applyTestCase(await getTestCase(testCaseId))
            } else {
              setGenerationError(updated.errorMessage || 'Generation failed')
            }
          }
        } catch {
          // SSE reconnect handles recovery.
        }
      }, 3000)
    } catch (err) {
      setGenerationError(err instanceof ApiError ? err.message : 'Generation failed')
    } finally {
      setBusy(null)
    }
  }

  async function onSaveCode() {
    if (!testCaseId) {
      return
    }
    setBusy('code')
    try {
      applyTestCase(await saveGeneratedCode(testCaseId, code))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to save generated code')
    } finally {
      setBusy(null)
    }
  }

  async function onExecute() {
    if (!testCaseId) {
      return
    }
    setBusy('execute')
    setExecutionEvents([])
    try {
      const started = await executeTestCase(testCaseId)
      setRun(started)
      const finished = await pollRun(started.id)
      setRun(finished)
      if (testCaseId) {
        applyTestCase(await getTestCase(testCaseId))
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to execute test')
    } finally {
      setBusy(null)
    }
  }

  async function onStop() {
    if (!run?.id) {
      return
    }
    try {
      setRun(await stopExecutionRun(run.id))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to stop execution')
    }
  }

  async function onDelete() {
    if (!testCaseId || !window.confirm('Delete this test case?')) {
      return
    }
    await deleteTestCase(testCaseId)
    navigate(`/projects/${projectId}`)
  }

  function applyTestCase(loaded: TestCase) {
    setTestCase(loaded)
    setForm({
      name: loaded.name,
      description: loaded.description ?? '',
      naturalLanguage: loaded.naturalLanguage ?? '',
    })
    setCode(loaded.generatedCode ?? '')
    setError(null)
  }

  return (
    <section className="page">
      <header className="page-header">
        <div>
          <p className="muted">
            <Link to="/tests">Tests</Link>
            {' · '}
            <Link to="/">Workspace</Link>
          </p>
          <h1>{testCase?.name ?? 'Test'}</h1>
          <p className="muted">
            Status: {testCase?.status ?? '—'}
            {testCase?.projectId ? ` · Application under project` : ''}
          </p>
        </div>
        <div className="toolbar">
          <button className="btn primary" type="button" onClick={() => void navigate('/')} disabled={busy !== null}>
            Run Test
          </button>
          <button className="btn" type="button" onClick={() => void onGenerate()} disabled={busy !== null}>
            Generate
          </button>
          <button className="btn" type="button" onClick={() => void onExecute()} disabled={busy !== null || !code}>
            Execute
          </button>
          <button className="btn" type="button" onClick={() => void onStop()} disabled={run?.status !== 'RUNNING'}>
            {run?.status === 'RUNNING' ? 'Stop Test' : 'Stop'}
          </button>
          <button className="btn" type="button" onClick={() => void onDelete()}>
            Delete
          </button>
        </div>
      </header>
      {error ? <p className="error-text">{error}</p> : null}
      {busy ? <p className="muted">Working…</p> : null}

      <details className="detail-section" open>
        <summary>Test Instructions</summary>
        <div className="detail-body">
          <form className="form-grid" onSubmit={onSave}>
            <label>
              Name
              <input
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                required
              />
            </label>
            <label>
              Instructions
              <textarea
                className="editor"
                rows={10}
                value={form.naturalLanguage}
                onChange={(event) => setForm({ ...form, naturalLanguage: event.target.value })}
                required
              />
            </label>
            <div className="toolbar">
              <button className="btn primary" type="submit" disabled={busy !== null}>
                Save
              </button>
              <button className="btn" type="button" onClick={() => void onAnalyze()} disabled={busy !== null}>
                Analyze
              </button>
            </div>
          </form>
        </div>
      </details>

      <details className="detail-section" open>
        <summary>Test Steps</summary>
        <div className="detail-body">
          <IntentPanel intent={intent} onClarify={(questionId, option) => void onClarify(questionId, option)} />
        </div>
      </details>

      <details className="detail-section">
        <summary>Latest Result</summary>
        <div className="detail-body">
          <ExecutionPanel run={run} events={executionEvents} />
        </div>
      </details>

      <details className="detail-section">
        <summary>Generated Test</summary>
        <div className="detail-body">
          <textarea
            className="editor code-editor"
            rows={14}
            value={code}
            onChange={(event) => setCode(event.target.value)}
          />
          <div className="toolbar">
            <button className="btn" type="button" onClick={() => void onSaveCode()} disabled={!code || busy !== null}>
              Save code
            </button>
            <button className="btn" type="button" onClick={() => void onGenerate()} disabled={busy !== null}>
              Regenerate
            </button>
          </div>
        </div>
      </details>

      <details className="detail-section">
        <summary>Evidence</summary>
        <div className="detail-body stack">
          <LiveBrowserPanel events={generationEvents} applicationUrl="" />
          <GenerationProgress events={generationEvents} error={generationError} phase={busy ?? 'idle'} />
          <LocatorMemoryPanel entries={locatorMemory} />
          {run?.id ? <ScreenshotTimeline runId={run.id} /> : null}
        </div>
      </details>
    </section>
  )
}

async function pollRun(id: string): Promise<ExecutionRun> {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const current = await getExecutionRun(id)
    if (current.status !== 'RUNNING') {
      return current
    }
    await new Promise((resolve) => window.setTimeout(resolve, 2000))
  }
  return getExecutionRun(id)
}
