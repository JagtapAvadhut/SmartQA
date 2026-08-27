import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { getAiHealth, getAiSettings } from '../api/settings'
import { getTestCase, updateTestCase } from '../api/testcases'
import {
  clarifyTestCase,
  executeTestCaseWithOptions,
  startGeneration,
  getGenerationRun,
  getExecutionRun,
  analyzeWorkspace,
  saveGeneratedCode,
  stopExecutionRun,
  validateTestCase,
  getValidationRuns,
  getValidationRun,
  type GenerationRun,
} from '../api/workflow'
import { getPipelineRun, getLatestPipeline, startGenerateAndValidate, stopPipelineRun, requestFixAndRebuild, type PipelineRun } from '../api/pipeline'
import { connectSse } from '../api/sse'
import { resolveRuntimeClarification } from '../api/runtimeClarifications'
import { ClarificationModal } from '../components/ClarificationModal'
import type { HealthCheckResult } from '../api/health'
import { DebugTracePanel } from '../components/debug/DebugTracePanel'
import { ScreenshotTimeline } from '../features/execution/ScreenshotTimeline'
import { CurrentAction } from '../features/execution/CurrentAction'
import { StepTimeline } from '../features/execution/StepTimeline'
import { ValidationHistory } from '../features/validation/ValidationHistory'
import { ORANGEHRM_INSTRUCTIONS, ORANGEHRM_URL, isValidHttpUrl } from '../features/workspace/example'
import { collapseRepeatedInstructions } from '../features/workspace/instructions'
import {
  StepBuilder,
  createEmptyStep,
  parseNaturalLanguageToSteps,
  stepsToNaturalLanguage,
  type StructuredStep,
} from '../features/workspace/StepBuilder'
import { friendlyError } from '../features/workspace/errors'
import { loadRecentTests, type RecentTest } from '../features/workspace/persist'
import { readWorkspaceDraft, writeWorkspaceDraft, clearWorkspaceDraft } from '../features/workspace/draft'
import {
  applicationLabel,
  formatDuration,
  humanAiDiagnosis,
  humanFailedStep,
  humanFailureWhy,
} from '../features/workspace/humanize'
import { PipelineTimeline } from '../features/workspace/PipelineTimeline'
import { readWorkspaceSession, type WorkspacePhase, writeWorkspaceSession, clearWorkspaceSession } from '../features/workspace/session'
import { eventLabel, computeGenerationProgress, computePipelineProgress } from '../features/workspace/eventLabels'
import { traceLogger } from '../services/traceLogger'
import type { AiHealthSnapshot, AiSettings, ExecutionRun, ProgressEvent, ValidationRun } from '../types/intent'
import { parseIntent, parseLocatorMemory } from '../types/intent'
import type { TestCase } from '../types/testcase'
import { formatLocator } from '../features/workspace/session'

interface OverviewPageProps {
  health: HealthCheckResult | null
  onRetryHealth: () => void
}

type WizardStep = 'create' | 'review' | 'generate' | 'result' | 'validate' | 'execute' | 'execution-result' | 'pipeline'

export function OverviewPage({ health, onRetryHealth }: OverviewPageProps) {
  const location = useLocation()
  const [applicationUrl, setApplicationUrl] = useState('')
  const [instructions, setInstructions] = useState('')
  const [instructionMode, setInstructionMode] = useState<'structured' | 'paragraph'>('paragraph')
  const [structuredSteps, setStructuredSteps] = useState<StructuredStep[]>([createEmptyStep(1)])
  const [urlError, setUrlError] = useState<string | null>(null)
  const [phase, setPhase] = useState<WorkspacePhase>('idle')
  const [error, setError] = useState<string | null>(null)
  const [errorDetails, setErrorDetails] = useState<string | null>(null)
  const [testCase, setTestCase] = useState<TestCase | null>(null)
  const [projectId, setProjectId] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [generationEvents, setGenerationEvents] = useState<ProgressEvent[]>([])
  const [executionEvents, setExecutionEvents] = useState<ProgressEvent[]>([])
  const [run, setRun] = useState<ExecutionRun | null>(null)
  const [recent, setRecent] = useState<RecentTest[]>([])
  const [settings, setSettings] = useState<AiSettings | null>(null)
  const [aiHealth, setAiHealth] = useState<AiHealthSnapshot | null>(null)
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const [advancedStepsOpen, setAdvancedStepsOpen] = useState(false)
  const [showTechnicalDetails, setShowTechnicalDetails] = useState(false)
  const [showDebugTrace, setShowDebugTrace] = useState(false)
  const [showEvidence, setShowEvidence] = useState(false)
  const [stopping, setStopping] = useState(false)
  const [validationRun, setValidationRun] = useState<ValidationRun | null>(null)
  const [validationHistory, setValidationHistory] = useState<ValidationRun[]>([])
  const [validating, setValidating] = useState(false)
  const [generationRun, setGenerationRun] = useState<GenerationRun | null>(null)
  const [showGeneratedCode, setShowGeneratedCode] = useState(false)
  const [executionProvider] = useState<'PLAYWRIGHT_JAVA'>('PLAYWRIGHT_JAVA')
  const [browserMode, setBrowserMode] = useState<'headed' | 'headless'>('headed')
  const [pipelineRun, setPipelineRun] = useState<PipelineRun | null>(null)
  const [pipelineEvents, setPipelineEvents] = useState<ProgressEvent[]>([])
  const genPollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const pipelinePollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const activePipelineIdRef = useRef<string | null>(null)
  const pipelineTerminalRef = useRef(false)
  const [runtimeClarification, setRuntimeClarification] = useState<{
    id: string
    question: string
    options: Array<{ id: string; label: string }>
  } | null>(null)

  const intent = useMemo(() => parseIntent(testCase?.intentContract ?? null), [testCase])
  const locatorMemory = useMemo(() => parseLocatorMemory(testCase?.locatorMemory ?? null), [testCase])
  const busy = ['creating', 'analyzing', 'generating', 'executing', 'pipeline-running', 'stopping'].includes(phase) || validating

  const wizardStep: WizardStep = useMemo(() => {
    if (phase === 'creating' || phase === 'stopping' || phase === 'pipeline-running' || phase === 'abandoned' || (pipelineRun && !['PASS', 'VALIDATED_NOT_EXECUTED', 'FAIL', 'BLOCKED', 'STOPPED', 'ABANDONED'].includes(pipelineRun.status) && phase !== 'idle')) {
      return 'pipeline'
    }
    if (pipelineRun && ['PASS', 'VALIDATED_NOT_EXECUTED', 'FAIL', 'BLOCKED', 'STOPPED', 'ABANDONED'].includes(pipelineRun.status) && (phase === 'completed' || phase === 'failed' || phase === 'stopped' || phase === 'blocked')) {
      return 'pipeline'
    }
    if (phase === 'generating') return 'generate'
    if (phase === 'executing') return 'execute'
    if (phase === 'generated' && validating) return 'validate'
    if (phase === 'completed' || phase === 'stopped') return 'execution-result'
    if (phase === 'failed' && executionEvents.length > 0) return 'execution-result'
    if (phase === 'failed' && generationEvents.length > 0 && !code) return 'generate'
    if (phase === 'generated' || (code && phase !== 'idle')) return 'result'
    if (phase === 'analysis-complete' && intent) return 'review'
    return 'create'
  }, [phase, intent, code, generationEvents.length, executionEvents.length, pipelineRun])

  const effectiveInstructions =
    instructionMode === 'structured' ? stepsToNaturalLanguage(structuredSteps) : instructions
  const canAnalyze =
    !busy &&
    effectiveInstructions.trim().length > 0 &&
    applicationUrl.trim().length > 0 &&
    (instructionMode === 'paragraph' || structuredSteps.some((s) => s.target.trim() || s.action === 'NAVIGATE' || s.value.trim()))
  const canGenerateAndValidate = canAnalyze
  const canGenerate = !busy && testCase != null && intent?.status === 'READY' && phase !== 'idle'
  const canExecute = !busy && code.trim().length > 0

  const eventTypes = useMemo(() => new Set(generationEvents.map((e) => e.type)), [generationEvents])
  const generationProgress = useMemo(
    () => computeGenerationProgress(eventTypes, phase === 'failed' && wizardStep === 'generate'),
    [eventTypes, phase, wizardStep],
  )
  const pipelineProgress = useMemo(
    () => computePipelineProgress(
      pipelineRun?.userProgress ?? [],
      pipelineRun?.userStageLabel,
      pipelineRun?.status,
    ),
    [pipelineRun],
  )

  const totalSteps = useMemo(() => {
    if (!intent?.scenarios) return 0
    return intent.scenarios.reduce((n, s) => n + (s.steps?.length ?? 0), 0)
  }, [intent])
  const assertionCount = useMemo(() => {
    if (!intent?.scenarios) return 0
    return intent.scenarios.reduce(
      (n, s) => n + (s.steps?.filter((st) => st.assertion).length ?? 0),
      0,
    )
  }, [intent])

  useEffect(() => {
    getAiSettings().then(setSettings).catch(() => undefined)
    getAiHealth().then(setAiHealth).catch(() => undefined)
    void refreshRecent()
    const draft = readWorkspaceDraft()
    if (draft) {
      setApplicationUrl((c) => (c.trim() ? c : draft.applicationUrl))
      setInstructions((c) => (c.trim() ? c : draft.instructions))
      if (draft.instructionMode === 'structured' || draft.instructionMode === 'paragraph') {
        setInstructionMode(draft.instructionMode)
      }
    }
    const session = readWorkspaceSession()
    if (!session.testCaseId) return
    getTestCase(session.testCaseId)
      .then((loaded) => {
        setTestCase(loaded)
        setProjectId(session.projectId)
        setInstructions((c) => (c.trim() ? c : collapseRepeatedInstructions(loaded.naturalLanguage ?? '')))
        setCode(loaded.generatedCode ?? '')
        if (loaded.generatedCode) setPhase('generated')
        else if (loaded.intentContract) setPhase('analysis-complete')
        refreshValidationHistory(loaded.id)
        void restoreSavedPipeline(session.pipelineRunId, loaded.id)
      })
      .catch(() => undefined)
    loadRecentTests()
      .then((items) => {
        const match = items.find((item) => item.testCase.id === session.testCaseId)
        if (match) {
          setApplicationUrl((c) => (c.trim() ? c : match.applicationUrl))
          setProjectId(match.projectId)
        }
      })
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    writeWorkspaceDraft({
      applicationUrl,
      instructions: instructionMode === 'structured' ? stepsToNaturalLanguage(structuredSteps) : instructions,
      instructionMode,
    })
  }, [applicationUrl, instructions, instructionMode, structuredSteps])

  useEffect(() => {
    const state = location.state as { loadTestId?: string; projectId?: string; resetWorkspace?: boolean } | null
    if (state?.resetWorkspace) {
      onNewTest()
      return
    }
    if (!state?.loadTestId) return
    getTestCase(state.loadTestId)
      .then((loaded) => {
        setTestCase(loaded)
        setProjectId(state.projectId ?? loaded.projectId)
        setInstructions(collapseRepeatedInstructions(loaded.naturalLanguage ?? ''))
        setInstructionMode('paragraph')
        setCode(loaded.generatedCode ?? '')
        writeWorkspaceSession(state.projectId ?? loaded.projectId, loaded.id)
        loadRecentTests()
          .then((items) => {
            const match = items.find((item) => item.testCase.id === loaded.id)
            if (match) setApplicationUrl(match.applicationUrl)
          })
          .catch(() => undefined)
      })
      .catch(() => undefined)
  }, [location.state])

  useEffect(() => {
    if (!pipelineRun?.id) return
    activePipelineIdRef.current = pipelineRun.id
    pipelineTerminalRef.current = ['PASS', 'VALIDATED_NOT_EXECUTED', 'FAIL', 'BLOCKED', 'STOPPED', 'ABANDONED'].includes(pipelineRun.status)
    return connectSse(`/api/pipelines/${pipelineRun.id}/stream`, {
      streamKey: pipelineRun.id,
      pipelineRunId: pipelineRun.id,
      generationRunId: pipelineRun.generationRunId ?? null,
      recoverState: async () => {
        try {
          const updated = await getPipelineRun(pipelineRun.id)
          applyPipelineSnapshot(updated)
        } catch {
          // polling continues
        }
      },
      handlers: {
        onEvent: (eventName, data) => {
          if (eventName === 'message') return
          const evt = data as ProgressEvent
          const details = evt.details && typeof evt.details === 'object'
            ? (evt.details as Record<string, unknown>)
            : {}
          const eventPipelineId = String(details.pipelineRunId ?? details.pipelineId ?? pipelineRun.id)
          if (activePipelineIdRef.current && eventPipelineId !== activePipelineIdRef.current) {
            return
          }
          if (pipelineTerminalRef.current) {
            return
          }
          setPipelineEvents((c) => [...c, evt])
          if (eventName === 'WAITING_FOR_CLARIFICATION') {
            const candidates = Array.isArray(details.candidates)
              ? (details.candidates as Array<Record<string, unknown>>)
              : []
            setRuntimeClarification({
              id: String(details.clarificationId ?? ''),
              question: evt.message || 'Multiple equally supported matches. Choose one.',
              options: candidates
                .map((candidate) => ({
                  id: String(candidate.candidateId ?? candidate.label ?? ''),
                  label: String(candidate.label ?? candidate.candidateId ?? 'Option'),
                }))
                .filter((option) => option.id),
            })
          }
          if (eventName === 'PIPELINE_PASSED' || eventName === 'PIPELINE_FAILED'
            || eventName === 'PIPELINE_BLOCKED' || eventName === 'PIPELINE_STOPPED') {
            void getPipelineRun(pipelineRun.id).then(applyPipelineTerminal).catch(() => undefined)
          } else if (evt.details && typeof evt.details === 'object') {
            setPipelineRun((current) => current ? {
              ...current,
              userStageLabel: String(
                (evt.details as Record<string, unknown>).userStage
                  ?? (evt.details as Record<string, unknown>).userStage
                  ?? current.userStageLabel
              ),
              status: (String((evt.details as Record<string, unknown>).status ?? current.status) as PipelineRun['status']),
            } : current)
          }
        },
      },
    })
  }, [pipelineRun?.id])

  useEffect(() => {
    if (!testCase?.id) return
    return connectSse(`/api/test-cases/${testCase.id}/generation/stream`, {
      streamKey: testCase.id,
      generationRunId: generationRun?.id ?? pipelineRun?.generationRunId ?? null,
      recoverState: async () => {
        const runId = generationRun?.id
        if (!runId) return
        try {
          const updated = await getGenerationRun(runId)
          setGenerationRun(updated)
          if (updated.status === 'COMPLETED') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            const loaded = await getTestCase(testCase.id)
            applyTestCase(loaded)
            if (!activePipelineIdRef.current || pipelineTerminalRef.current) {
              setPhase('generated')
            }
          } else if (updated.status === 'FAILED' || updated.status === 'STOPPED') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            if (!activePipelineIdRef.current || pipelineTerminalRef.current) {
              setError(updated.errorMessage || 'Test generation failed')
              setPhase('failed')
            }
          }
        } catch {
          // Polling continues as fallback.
        }
      },
      handlers: {
        onEvent: (eventName, data) => {
          if (eventName === 'message') return
          setGenerationEvents((c) => [...c, data as ProgressEvent])
          if (eventName === 'GENERATION_COMPLETE') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            getTestCase(testCase.id).then((loaded) => {
              applyTestCase(loaded)
              if (!activePipelineIdRef.current || pipelineTerminalRef.current) {
                setPhase('generated')
              }
            }).catch(() => undefined)
          }
          if (eventName === 'GENERATION_ERROR') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            if (!activePipelineIdRef.current || pipelineTerminalRef.current) {
              setPhase('failed')
              const evt = data as ProgressEvent
              setError(evt.message || 'Test generation failed')
            }
          }
        },
      },
    })
  }, [testCase?.id, generationRun?.id])

  useEffect(() => {
    if (!run?.id) return
    return connectSse(`/api/execution-runs/${run.id}/stream`, {
      streamKey: run.id,
      recoverState: async () => {
        try {
          const updated = await getExecutionRun(run.id)
          setRun(updated)
          if (updated.status !== 'RUNNING') {
            setPhase(updated.status === 'PASSED' || updated.exitCode === 0
              ? 'completed'
              : updated.status === 'STOPPED'
                ? 'stopped'
                : 'failed')
          }
        } catch {
          // Execution polling remains the fallback.
        }
      },
      handlers: {
        onEvent: (eventName, data) => {
          if (eventName === 'message') return
          setExecutionEvents((c) => [...c, data as ProgressEvent])
          if (eventName === 'EXECUTION_STOPPED') {
            setStopping(false)
            setPhase('stopped')
          }
        },
      },
    })
  }, [run?.id])

  useEffect(() => {
    return () => {
      if (genPollRef.current) clearInterval(genPollRef.current)
    }
  }, [])

  async function refreshRecent() {
    try { setRecent(await loadRecentTests()) } catch { setRecent([]) }
  }

  function refreshValidationHistory(testCaseId: string) {
    getValidationRuns(testCaseId)
      .then((runs) => {
        setValidationHistory(runs)
        if (runs.length > 0) setValidationRun(runs[0])
      })
      .catch(() => {})
  }

  function applyError(err: unknown) {
    const mapped = friendlyError(err)
    setError(mapped.message)
    setErrorDetails(mapped.details)
    setPhase('failed')
    traceLogger.error('UI', 'ERROR_DISPLAYED', mapped.message, { details: mapped.details }, err)
  }

  function applyTestCase(loaded: TestCase, nextProjectId = projectId, keepInstructions = false) {
    setTestCase(loaded)
    if (!keepInstructions) {
      const nl = collapseRepeatedInstructions(loaded.naturalLanguage ?? '')
      setInstructions(nl)
      setStructuredSteps(parseNaturalLanguageToSteps(nl))
    }
    setCode(loaded.generatedCode ?? '')
    setProjectId(nextProjectId)
    if (nextProjectId) writeWorkspaceSession(nextProjectId, loaded.id)
  }

  function onLoadExample() {
    setApplicationUrl(ORANGEHRM_URL)
    setInstructions(ORANGEHRM_INSTRUCTIONS)
    setStructuredSteps(parseNaturalLanguageToSteps(ORANGEHRM_INSTRUCTIONS))
    setInstructionMode('paragraph')
    setUrlError(null)
    setError(null)
    if (phase === 'idle' || phase === 'failed') setPhase('idle')
  }

  function onNewTest() {
    clearWorkspaceSession()
    clearWorkspaceDraft()
    setApplicationUrl('')
    setInstructions('')
    setStructuredSteps([createEmptyStep(1)])
    setInstructionMode('paragraph')
    setTestCase(null)
    setProjectId(null)
    setCode('')
    setPipelineRun(null)
    setPipelineEvents([])
    setGenerationEvents([])
    setExecutionEvents([])
    setRun(null)
    setGenerationRun(null)
    setValidationRun(null)
    setError(null)
    setErrorDetails(null)
    setUrlError(null)
    setPhase('idle')
    pipelineTerminalRef.current = false
    activePipelineIdRef.current = null
    if (pipelinePollRef.current) {
      clearInterval(pipelinePollRef.current)
      pipelinePollRef.current = null
    }
  }

  async function recoverDurableState() {
    try {
      if (pipelineRun?.id) {
        const updated = await getPipelineRun(pipelineRun.id)
        applyPipelineSnapshot(updated)
      }
      if (testCase?.id) {
        const loaded = await getTestCase(testCase.id)
        applyTestCase(loaded)
      }
      if (generationRun?.id) {
        setGenerationRun(await getGenerationRun(generationRun.id))
      }
    } catch {
      // keep current durable UI state
    }
  }

  async function onAnalyze() {
    const url = applicationUrl.trim()
    if (!isValidHttpUrl(url)) { setUrlError('Please enter a valid application URL.'); return }
    const currentInstructions =
      instructionMode === 'structured'
        ? stepsToNaturalLanguage(structuredSteps)
        : collapseRepeatedInstructions(instructions)
    if (!currentInstructions.trim()) { setError('Please describe the test you want SmartQA to perform.'); return }
    setUrlError(null)
    setError(null)
    setErrorDetails(null)
    setPhase('analyzing')
    setGenerationEvents([])
    setInstructions(currentInstructions)
    traceLogger.start('ANALYZE', { applicationUrl: url, instructionLength: currentInstructions.trim().length, operation: 'ANALYZE' })
    try {
      const analyzed = await analyzeWorkspace({
        applicationUrl: url,
        instructions: currentInstructions,
        projectId,
        testCaseId: testCase?.id,
        structuredSteps:
          instructionMode === 'structured'
            ? structuredSteps.map((step) => ({
                id: step.id,
                action: step.action.toLowerCase(),
                target: step.target.trim() || null,
                value: step.value.trim() || null,
                assertion: step.assertion.trim() || null,
                location: step.location,
              }))
            : null,
      })
      setProjectId(analyzed.project.id)
      applyTestCase(analyzed.testCase, analyzed.project.id, true)
      writeWorkspaceSession(analyzed.project.id, analyzed.testCase.id)
      setPhase('analysis-complete')
      await refreshRecent()
    } catch (err) { applyError(err) }
  }

  async function onClarify(questionId: string, option: string) {
    if (!testCase) return
    setPhase('analyzing')
    try {
      const updated = await clarifyTestCase(testCase.id, [{ questionId, selectedOption: option }])
      applyTestCase(updated, projectId, true)
      setPhase('analysis-complete')
    } catch (err) { applyError(err) }
  }

  const PIPELINE_TERMINAL = ['PASS', 'VALIDATED_NOT_EXECUTED', 'FAIL', 'BLOCKED', 'STOPPED', 'ABANDONED']

  function applyPipelineSnapshot(updated: PipelineRun) {
    if (activePipelineIdRef.current && updated.id !== activePipelineIdRef.current) {
      return
    }
    if (pipelineTerminalRef.current && !PIPELINE_TERMINAL.includes(updated.status)) {
      return
    }
    if (PIPELINE_TERMINAL.includes(updated.status)) {
      applyPipelineTerminal(updated)
      return
    }
    setPipelineRun(updated)
    if (updated.testCase) {
      applyTestCase(updated.testCase, updated.projectId ?? projectId, true)
    }
  }

  function startPipelinePolling(pipelineId: string) {
    if (pipelinePollRef.current) clearInterval(pipelinePollRef.current)
    pipelinePollRef.current = setInterval(() => {
      void getPipelineRun(pipelineId).then(applyPipelineSnapshot).catch(() => undefined)
    }, 2000)
  }

  async function restoreSavedPipeline(pipelineRunId: string | null, testCaseId: string) {
    let run: PipelineRun | null = null
    if (pipelineRunId) {
      try {
        run = await getPipelineRun(pipelineRunId)
      } catch {
        run = null
      }
    }
    if (!run) {
      run = await getLatestPipeline(testCaseId)
    }
    if (!run) return
    activePipelineIdRef.current = run.id
    setPipelineRun(run)
    if (run.projectId && (run.testCaseId || run.testCase?.id)) {
      writeWorkspaceSession(run.projectId, run.testCaseId ?? run.testCase?.id as string, run.id)
    }
    if (PIPELINE_TERMINAL.includes(run.status)) {
      applyPipelineTerminal(run)
      return
    }
    pipelineTerminalRef.current = false
    setPhase('pipeline-running')
    startPipelinePolling(run.id)
  }

  function applyPipelineTerminal(updated: PipelineRun) {
    if (activePipelineIdRef.current && updated.id !== activePipelineIdRef.current) {
      return
    }
    pipelineTerminalRef.current = true
    setPipelineRun(updated)
    if (updated.testCase) {
      applyTestCase(updated.testCase, updated.projectId ?? projectId, true)
      if (updated.projectId) writeWorkspaceSession(updated.projectId, updated.testCase.id, updated.id)
    }
    if (updated.status === 'PASS') {
      setPhase('completed')
      setError(null)
    } else if (updated.status === 'BLOCKED') {
      setPhase('blocked')
      setError(updated.errorMessage || 'Clarification needed')
    } else if (updated.status === 'STOPPED') {
      setPhase('stopped')
    } else if (updated.status === 'ABANDONED') {
      setPhase('abandoned')
      setError(updated.errorMessage || updated.finalSummary || 'Pipeline was interrupted')
    } else {
      setPhase('failed')
      setError(updated.errorMessage || updated.finalSummary || 'Pipeline failed')
    }
    if (pipelinePollRef.current) {
      clearInterval(pipelinePollRef.current)
      pipelinePollRef.current = null
    }
    void refreshRecent()
  }

  async function onGenerateAndValidate() {
    const url = applicationUrl.trim()
    if (!isValidHttpUrl(url)) { setUrlError('Please enter a valid application URL.'); return }
    const currentInstructions =
      instructionMode === 'structured'
        ? stepsToNaturalLanguage(structuredSteps)
        : collapseRepeatedInstructions(instructions)
    if (!currentInstructions.trim()) { setError('Please describe the test you want SmartQA to perform.'); return }
    setUrlError(null)
    setError(null)
    setErrorDetails(null)
    setPipelineEvents([])
    setGenerationEvents([])
    setExecutionEvents([])
    setInstructions(currentInstructions)
    pipelineTerminalRef.current = false
    activePipelineIdRef.current = null
    setPhase('creating')
    traceLogger.start('GENERATE_AND_VALIDATE', {
      applicationUrl: url,
      instructionLength: currentInstructions.trim().length,
      operation: 'GENERATE_AND_VALIDATE',
    })
    try {
      const started = await startGenerateAndValidate({
        applicationUrl: url,
        instructions: currentInstructions,
        projectId,
        testCaseId: testCase?.id,
        browserMode,
        headless: browserMode === 'headless',
        structuredSteps:
          instructionMode === 'structured'
            ? structuredSteps.map((step) => ({
                id: step.id,
                action: step.action.toLowerCase(),
                target: step.target.trim() || null,
                value: step.value.trim() || null,
                assertion: step.assertion.trim() || null,
                location: step.location,
              }))
            : null,
      })
      setPipelineRun(started)
      activePipelineIdRef.current = started.id
      pipelineTerminalRef.current = false
      setPhase('pipeline-running')
      if (started.testCaseId && started.projectId) {
        writeWorkspaceSession(started.projectId, started.testCaseId, started.id)
      }
      startPipelinePolling(started.id)
    } catch (err) {
      applyError(err)
    }
  }

  async function onStopPipeline() {
    if (!pipelineRun?.id || stopping) return
    if (['PASS', 'FAIL', 'STOPPED', 'BLOCKED', 'ABANDONED', 'VALIDATED_NOT_EXECUTED'].includes(pipelineRun.status)) {
      return
    }
    setStopping(true)
    setPhase('stopping')
    try {
      const stopped = await stopPipelineRun(pipelineRun.id)
      applyPipelineTerminal(stopped)
    } catch (err) {
      applyError(err)
    } finally {
      setStopping(false)
    }
  }

  async function onGenerate() {
    if (!testCase) return
    setPhase('generating')
    setError(null)
    setErrorDetails(null)
    setGenerationEvents([])
    traceLogger.start('GENERATE_TEST', { applicationUrl, testCaseId: testCase.id, operation: 'GENERATE_TEST' })
    try {
      const genRun = await startGeneration(testCase.id)
      setGenerationRun(genRun)
      genPollRef.current = setInterval(async () => {
        try {
          const updated = await getGenerationRun(genRun.id)
          setGenerationRun(updated)
          if (updated.status === 'COMPLETED' || updated.status === 'FAILED' || updated.status === 'STOPPED') {
            if (genPollRef.current) clearInterval(genPollRef.current)
            if (updated.status === 'COMPLETED') {
              const loaded = await getTestCase(testCase.id)
              applyTestCase(loaded)
              setPhase('generated')
              await refreshRecent()
            } else {
              setError(updated.errorMessage || 'Test generation failed')
              setPhase('failed')
            }
          }
        } catch { /* SSE handles it too */ }
      }, 3000)
    } catch (err) { applyError(err) }
  }

  async function onExecute() {
    if (!testCase) return
    setPhase('executing')
    setExecutionEvents([])
    setError(null)
    setErrorDetails(null)
    setStopping(false)
    traceLogger.start('EXECUTE_TEST', { applicationUrl, testCaseId: testCase.id, operation: 'EXECUTE_TEST' })
    try {
      if (code !== (testCase.generatedCode ?? '')) applyTestCase(await saveGeneratedCode(testCase.id, code))
      const started = await executeTestCaseWithOptions(testCase.id, {
        executionProvider,
        browserMode,
        headless: browserMode === 'headless',
      })
      setRun(started)
      const finished = await pollRun(started.id)
      setRun(finished)
      applyTestCase(await getTestCase(testCase.id))
      setPhase(finished.status === 'PASSED' || finished.exitCode === 0 ? 'completed' : finished.status === 'STOPPED' ? 'stopped' : 'failed')
      await refreshRecent()
    } catch (err) { applyError(err) }
  }

  async function onStop() {
    if (!run?.id || stopping) return
    setStopping(true)
    try {
      setRun(await stopExecutionRun(run.id))
      setPhase('stopped')
    } catch (err) { applyError(err) }
    finally { setStopping(false) }
  }

  async function onValidate() {
    if (!testCase || validating) return
    setValidating(true)
    setValidationRun(null)
    try {
      const started = await validateTestCase(testCase.id)
      setValidationRun(started)
      const finished = await pollValidation(started.id)
      setValidationRun(finished)
      refreshValidationHistory(testCase.id)
    } catch (err) { applyError(err) }
    finally { setValidating(false) }
  }

  async function onSave() {
    if (!testCase) return
    try {
      applyTestCase(await updateTestCase(testCase.id, { name: testCase.name, description: testCase.description ?? '', naturalLanguage: collapseRepeatedInstructions(instructions) }))
    } catch (err) { applyError(err) }
  }

  function onCopy() { if (code) void navigator.clipboard.writeText(code) }

  function onDownload() {
    const blob = new Blob([code], { type: 'text/plain' })
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = `${(testCase?.name ?? 'SmartQATest').replaceAll(' ', '')}.java`
    link.click()
    URL.revokeObjectURL(href)
  }

  function loadRecent(item: RecentTest) {
    setApplicationUrl(item.applicationUrl)
    setInstructions(collapseRepeatedInstructions(item.testCase.naturalLanguage ?? ''))
    setCode(item.testCase.generatedCode ?? '')
    setProjectId(item.projectId)
    setTestCase(item.testCase)
    writeWorkspaceSession(item.projectId, item.testCase.id)
    if (item.testCase.generatedCode) setPhase('generated')
    else if (item.testCase.intentContract) setPhase('analysis-complete')
    else setPhase('idle')
    setError(null)
    setGenerationEvents([])
    setExecutionEvents([])
    setShowDebugTrace(false)
    refreshValidationHistory(item.testCase.id)
  }

  function goBack() {
    if (wizardStep === 'review') setPhase('idle')
    else if (wizardStep === 'result') setPhase('analysis-complete')
    else if (wizardStep === 'execution-result') { if (code) setPhase('generated'); else setPhase('idle') }
  }

  function statusIcon(status: string) {
    switch (status) {
      case 'PASSED': return '✓'
      case 'FAILED': return '✗'
      case 'READY': return '●'
      case 'DRAFT': return '○'
      case 'ANALYSIS_FAILED': return '⚠'
      case 'RUNNING': return '◌'
      default: return '○'
    }
  }

  const backendDown = health != null && !health.ok
  const backendChecking = health == null

  const executionStepCount = executionEvents.filter((e) => e.type === 'STEP_COMPLETED' || e.type === 'STEP_PASSED').length
  const executionPassed = phase === 'completed'

  return (
    <section className="page workspace-page mx-auto max-w-3xl">
      <header className="mb-7">
        <p className="mb-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-accent">Workspace</p>
        <h1 className="text-[28px] font-semibold tracking-tight">Create a test</h1>
        <p className="mt-1 text-sm text-muted">Describe what to do in plain language. SmartQA opens the site, runs the steps, and reports pass or fail.</p>
      </header>

      {backendChecking && <p className="muted">Checking SmartQA backend…</p>}
      {backendDown && (
        <div className="card" style={{ borderColor: 'var(--danger)' }}>
          <p className="error-text" style={{ fontWeight: 600, fontSize: 16 }}>SmartQA backend is not connected.</p>
          <p className="muted">Start the backend, then retry.</p>
          <div className="toolbar" style={{ marginTop: 8 }}>
            <button className="btn primary" type="button" onClick={onRetryHealth}>Retry Connection</button>
            <button className="btn" type="button" onClick={() => setShowTechnicalDetails((v) => !v)}>
              {showTechnicalDetails ? 'Hide details' : 'View Details'}
            </button>
          </div>
          {showTechnicalDetails && health?.reason ? <pre className="log-block">{health.reason}</pre> : null}
        </div>
      )}

      {/* ERROR CARD */}
      {error && wizardStep !== 'generate' && wizardStep !== 'pipeline' && (
        <div className="card" style={{ borderColor: 'var(--danger)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <span style={{ color: 'var(--danger)', fontSize: 20 }}>✗</span>
            <strong style={{ color: 'var(--danger)' }}>Something went wrong</strong>
          </div>
          <p style={{ margin: '4px 0 12px' }}>{error}</p>
          {errorDetails && (
            <button className="btn" type="button" onClick={() => setShowTechnicalDetails((v) => !v)} style={{ marginBottom: 8 }}>
              {showTechnicalDetails ? 'Hide technical details' : 'View Details'}
            </button>
          )}
          {showTechnicalDetails && errorDetails && <pre className="log-block">{errorDetails}</pre>}
          <div className="toolbar" style={{ marginTop: 8 }}>
            {canGenerateAndValidate && (
              <button className="btn primary" type="button" onClick={() => void onGenerateAndValidate()}>
                Run Again
              </button>
            )}
            <button className="btn" type="button" onClick={() => setShowDebugTrace(true)}>View Debug Details</button>
          </div>
        </div>
      )}

      {/* WIZARD CONTENT */}
      <div className="workspace-main">

        {/* STEP 1: CREATE */}
        {wizardStep === 'create' && (
          <article className="card workspace-create-card">
            <div className="form-grid">
              <label className="text-sm font-medium text-text">
                Application URL
                <input
                  value={applicationUrl}
                  onChange={(e) => { setApplicationUrl(e.target.value); setUrlError(null) }}
                  placeholder="https://www.automall.ae/en/"
                  inputMode="url"
                />
              </label>
              {urlError && <p className="error-text">{urlError}</p>}
              <label className="text-sm font-medium text-text">
                Instructions
                <textarea
                  className="editor instruction-editor min-h-[240px] leading-6"
                  rows={10}
                  value={instructionMode === 'structured' ? stepsToNaturalLanguage(structuredSteps) : instructions}
                  onChange={(e) => {
                    setInstructionMode('paragraph')
                    setInstructions(e.target.value)
                  }}
                  placeholder={'Open the homepage\nClick Buy a Car\nSearch volvo in Brand & Model\nCheck VOLVO\nVerify 5 cars matching your search'}
                  disabled={busy || (instructionMode === 'structured' && advancedStepsOpen)}
                />
              </label>
              <p className="text-xs text-muted">
                One action per line works best. SmartQA compiles this into browser steps automatically.
              </p>
              <div className="toolbar primary-actions">
                <button className="btn primary" type="button" onClick={() => void onGenerateAndValidate()} disabled={!canGenerateAndValidate}>
                  {phase === 'pipeline-running' ? 'Running…' : 'Generate & Validate'}
                </button>
                <button className="btn" type="button" onClick={onNewTest} disabled={busy}>New test</button>
                <button className="btn" type="button" onClick={() => void recoverDurableState()} disabled={busy}>Refresh</button>
                <button className="btn" type="button" onClick={onLoadExample} disabled={busy}>Load example</button>
              </div>

              <div className="advanced-block">
                <button
                  className="btn"
                  type="button"
                  onClick={() => {
                    setAdvancedStepsOpen((v) => {
                      const next = !v
                      if (next && instructionMode !== 'structured') {
                        setStructuredSteps(parseNaturalLanguageToSteps(instructions))
                        setInstructionMode('structured')
                      }
                      if (!next && instructionMode === 'structured') {
                        setInstructions(stepsToNaturalLanguage(structuredSteps))
                        setInstructionMode('paragraph')
                      }
                      return next
                    })
                  }}
                >
                  {advancedStepsOpen ? 'Hide Advanced Steps' : 'Advanced Steps'}
                </button>
                {advancedStepsOpen && (
                  <div style={{ marginTop: 12 }}>
                    <p className="muted" style={{ fontSize: 12, marginBottom: 8 }}>
                      Location is a search hint, not a fixed click position.
                    </p>
                    <StepBuilder steps={structuredSteps} onChange={setStructuredSteps} disabled={busy} />
                  </div>
                )}
              </div>

              {recent.length > 0 && (
                <div className="mt-6 border-t border-border pt-5">
                  <h3 className="mb-3 text-xs font-semibold uppercase tracking-[0.12em] text-muted">Recent tests</h3>
                  <ul className="grid gap-2">
                    {recent.slice(0, 5).map((item) => (
                      <li key={item.testCase.id}>
                        <button
                          className="flex w-full items-center justify-between gap-3 rounded-xl border border-border bg-bg/40 px-3 py-3 text-left hover:border-accent/40"
                          type="button"
                          onClick={() => loadRecent(item)}
                        >
                          <span className="flex min-w-0 items-center gap-2">
                            <span className={item.testCase.status === 'PASSED' ? 'text-ok' : item.testCase.status === 'FAILED' ? 'text-danger' : 'text-muted'}>
                              {statusIcon(item.testCase.status)}
                            </span>
                            <strong className="truncate">{item.testCase.name}</strong>
                          </span>
                          <span className="truncate text-xs text-muted">{applicationLabel(item.applicationUrl)}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                  <Link className="mt-3 inline-block text-sm text-muted hover:text-accent" to="/tests">View all tests →</Link>
                </div>
              )}

              <div style={{ marginTop: 8 }}>
                <button className="btn" type="button" onClick={() => setAdvancedOpen((v) => !v)} style={{ fontSize: 12 }}>
                  {advancedOpen ? 'Hide Advanced Options' : 'Show Advanced Options'}
                </button>
                {advancedOpen && (
                  <div style={{ marginTop: 12 }}>
                    <div className="toolbar" style={{ marginBottom: 12 }}>
                      <button className="btn" type="button" onClick={() => void onAnalyze()} disabled={!canAnalyze}>
                        {phase === 'analyzing' ? 'Analyzing…' : 'Analyze'}
                      </button>
                      <button className="btn" type="button" onClick={() => void onGenerate()} disabled={!canGenerate}>
                        Generate
                      </button>
                      <button className="btn" type="button" onClick={() => void onValidate()} disabled={!canExecute || validating}>
                        Validate
                      </button>
                      <button className="btn" type="button" onClick={() => void onExecute()} disabled={!canExecute}>
                        Execute
                      </button>
                    </div>
                    <dl className="kv">
                      <div><dt>AI provider</dt><dd>{settings?.primaryProvider ?? settings?.provider ?? '—'}</dd></div>
                      <div><dt>Fallback</dt><dd>{settings?.fallbackProvider || 'none'}</dd></div>
                      {(aiHealth?.providers ?? []).map((item) => (
                        <div key={item.provider}>
                          <dt>{item.provider === 'gemini' ? 'Gemini' : item.provider === 'ollama' ? 'Ollama' : item.provider}</dt>
                          <dd>{item.status === 'AVAILABLE' || item.status === 'COLD' ? 'Ready' : 'Unavailable'}</dd>
                        </div>
                      ))}
                      <div>
                        <dt>Browser mode</dt>
                        <dd>
                          <select value={browserMode} onChange={(e) => setBrowserMode(e.target.value as 'headed' | 'headless')}>
                            <option value="headed">Visible (headed)</option>
                            <option value="headless">Headless</option>
                          </select>
                        </dd>
                      </div>
                      <div><dt>More</dt><dd><Link to="/settings">Open settings</Link></dd></div>
                    </dl>
                  </div>
                )}
              </div>
            </div>
          </article>
        )}

        {/* ONE-CLICK PIPELINE */}
        {wizardStep === 'pipeline' && (
          <article className="card">
            <div className="mb-5 flex items-center justify-between gap-3">
              <div>
                <p className="mb-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-accent">Run</p>
                <h2 className="m-0 text-xl font-semibold">
                {pipelineRun?.status === 'PASS' && 'Test passed'}
                {pipelineRun?.status === 'VALIDATED_NOT_EXECUTED' && 'Validated (execution skipped)'}
                {pipelineRun?.status === 'FAIL' && 'Test failed'}
                {pipelineRun?.status === 'BLOCKED' && 'Needs your input'}
                {pipelineRun?.status === 'STOPPED' && 'Stopped'}
                {pipelineRun?.status === 'ABANDONED' && 'Abandoned after restart'}
                {(!pipelineRun || pipelineRun.status === 'RUNNING' || pipelineRun.status === 'QUEUED') && 'Running your test'}
                </h2>
              </div>
              {(phase === 'pipeline-running') && (
                <button className="btn" type="button" disabled={stopping} onClick={() => void onStopPipeline()}>
                  {stopping ? 'Stopping…' : 'Stop Test'}
                </button>
              )}
            </div>

            {(!pipelineRun || pipelineRun.status === 'RUNNING' || pipelineRun.status === 'QUEUED') && (
              <>
                <PipelineTimeline steps={pipelineProgress} currentLabel={pipelineRun?.userStageLabel} />
                {(pipelineRun?.executionRunId || pipelineRun?.testCaseId) ? (
                  <div style={{ marginTop: 16 }}>
                    <ScreenshotTimeline runId={pipelineRun.executionRunId || pipelineRun.testCaseId} />
                  </div>
                ) : null}
              </>
            )}

            {pipelineRun?.status === 'PASS' && (
              <div className="result-pass">
                <p className="result-banner ok">✅ Test Passed</p>
                <dl className="kv result-summary">
                  <div><dt>Application</dt><dd>{applicationLabel(pipelineRun.applicationUrl ?? applicationUrl)}</dd></div>
                  <div><dt>Steps</dt><dd>{totalSteps > 0 ? `${totalSteps} / ${totalSteps}` : 'Completed'}</dd></div>
                  <div><dt>Assertions</dt><dd>{assertionCount > 0 ? `${assertionCount} / ${assertionCount}` : '—'}</dd></div>
                  <div><dt>Validation</dt><dd>PASSED</dd></div>
                  <div><dt>Execution</dt><dd>PASSED</dd></div>
                  <div><dt>Duration</dt><dd>{formatDuration(pipelineRun.durationMs)}</dd></div>
                </dl>
                {pipelineRun.finalSummary ? <p className="muted">{pipelineRun.finalSummary}</p> : null}
                <div className="toolbar" style={{ marginTop: 12 }}>
                  <button className="btn primary" type="button" onClick={() => void onGenerateAndValidate()} disabled={busy}>Run Again</button>
                  <button className="btn" type="button" onClick={() => setShowGeneratedCode(true)}>View Test</button>
                  <button className="btn" type="button" onClick={() => setShowEvidence((v) => !v)}>
                    {showEvidence ? 'Hide Evidence' : 'View Evidence'}
                  </button>
                  <button className="btn" type="button" onClick={() => { setPipelineRun(null); setPhase('idle'); setShowGeneratedCode(false) }}>Edit Test</button>
                </div>
                {showGeneratedCode && (
                  <div style={{ marginTop: 12 }}>
                    <textarea className="editor code-editor" rows={14} value={code || pipelineRun.testCase?.generatedCode || ''} readOnly />
                  </div>
                )}
                {showEvidence && (
                  <div style={{ marginTop: 12 }}>
                    {(pipelineRun.executionRunId || pipelineRun.testCaseId) ? <ScreenshotTimeline runId={pipelineRun.executionRunId || pipelineRun.testCaseId} /> : null}
                    <ValidationHistory runs={validationHistory} />
                    <button className="btn" type="button" style={{ marginTop: 8 }} onClick={() => setShowDebugTrace(true)}>
                      View Debug Details
                    </button>
                  </div>
                )}
              </div>
            )}

            {pipelineRun?.status === 'VALIDATED_NOT_EXECUTED' && (
              <div className="result-pass">
                <p className="result-banner warn">Validated — final execution was skipped</p>
                <p className="muted">
                  This is not a full pipeline PASS. Quality gate and independent validation succeeded;
                  browser execution did not run.
                </p>
                <dl className="kv result-summary">
                  <div><dt>Validation</dt><dd>PASSED</dd></div>
                  <div><dt>Execution</dt><dd>SKIPPED</dd></div>
                  <div><dt>Pipeline PASS</dt><dd>No</dd></div>
                </dl>
                <div className="toolbar" style={{ marginTop: 12 }}>
                  <button className="btn primary" type="button" onClick={() => void onGenerateAndValidate()} disabled={busy}>
                    Run with execution
                  </button>
                  <button className="btn" type="button" onClick={() => { setPipelineRun(null); setPhase('idle') }}>Edit Test</button>
                </div>
              </div>
            )}

            {pipelineRun?.status === 'FAIL' && (
              <div className="result-fail">
                <p className="result-banner danger">❌ Test Failed</p>
                <dl className="kv result-summary">
                  <div><dt>Failed step</dt><dd>{humanFailedStep(pipelineRun.diagnosis)}</dd></div>
                  <div><dt>Why it failed</dt><dd>{humanFailureWhy(pipelineRun.diagnosis, pipelineRun.errorMessage)}</dd></div>
                  {pipelineRun.diagnosis?.failureEvidence?.expected ? (
                    <div><dt>Expected</dt><dd>{pipelineRun.diagnosis.failureEvidence.expected}</dd></div>
                  ) : null}
                  {pipelineRun.diagnosis?.failureEvidence?.actual ? (
                    <div><dt>Actual</dt><dd>{pipelineRun.diagnosis.failureEvidence.actual}</dd></div>
                  ) : null}
                  {humanAiDiagnosis(pipelineRun.diagnosis) ? (
                    <div><dt>AI diagnosis</dt><dd>{humanAiDiagnosis(pipelineRun.diagnosis)}</dd></div>
                  ) : null}
                  {pipelineRun.diagnosis?.details?.understoodIntent ? (
                    <div><dt>What SmartQA understood</dt><dd>{String(pipelineRun.diagnosis.details.understoodIntent)}</dd></div>
                  ) : null}
                  {pipelineRun.diagnosis?.details?.foundTarget ? (
                    <div><dt>What SmartQA found</dt><dd>{String(pipelineRun.diagnosis.details.foundTarget)}</dd></div>
                  ) : pipelineRun.status === 'FAIL' ? (
                    <div><dt>What SmartQA could not verify</dt><dd>Target not present / not actionable</dd></div>
                  ) : null}
                  <div><dt>Attempts</dt><dd>{pipelineRun.attempt}</dd></div>
                  <div>
                    <dt>Recovered automatically</dt>
                    <dd>
                      {pipelineRun.diagnosis?.autoRecoverySucceeded
                        ? 'Yes'
                        : pipelineRun.diagnosis?.autoRecoveryAttempted || pipelineRun.diagnosis?.autoHealAttempted
                          ? 'No'
                          : 'No'}
                    </dd>
                  </div>
                </dl>
                {pipelineRun.diagnosis?.requiresSourceFix && (
                  <div className="card" style={{ marginTop: 12, borderColor: 'var(--accent)' }}>
                    <p style={{ margin: 0, fontWeight: 600 }}>SmartQA found a possible engine issue.</p>
                    <p className="muted">
                      Component: {pipelineRun.diagnosis.sourceFix?.component || pipelineRun.diagnosis.responsibleComponent || 'Engine'}
                    </p>
                    <button
                      className="btn primary"
                      type="button"
                      disabled={busy}
                      style={{ marginTop: 8 }}
                      onClick={() => void (async () => {
                        try {
                          setError(null)
                          setErrorDetails(null)
                          setPhase('pipeline-running')
                          await requestFixAndRebuild(pipelineRun.id)
                          const refreshed = await getPipelineRun(pipelineRun.id)
                          setPipelineRun(refreshed)
                          setPhase('failed')
                        } catch (err) {
                          const mapped = friendlyError(err)
                          setError(mapped.message)
                          setErrorDetails(mapped.details)
                          setPhase('failed')
                        }
                      })()}
                    >
                      Fix &amp; Rebuild
                    </button>
                  </div>
                )}
                <div className="toolbar" style={{ marginTop: 12 }}>
                  <button className="btn primary" type="button" onClick={() => void onGenerateAndValidate()} disabled={busy}>Run Again</button>
                  <button className="btn" type="button" onClick={() => { setPipelineRun(null); setPhase('idle') }}>Edit Test</button>
                  {pipelineRun.diagnosis?.screenshotPath || pipelineRun.diagnosis?.failureEvidence?.screenshotPath ? (
                    <button className="btn" type="button" onClick={() => setShowEvidence(true)}>View Screenshot</button>
                  ) : null}
                  {pipelineRun.diagnosis?.failureEvidence?.domExcerpt || pipelineRun.diagnosis?.failureEvidence?.visibleTextExcerpt ? (
                    <button className="btn" type="button" onClick={() => setShowEvidence(true)}>View DOM Evidence</button>
                  ) : null}
                  {humanAiDiagnosis(pipelineRun.diagnosis) ? (
                    <button className="btn" type="button" onClick={() => setShowEvidence(true)}>View AI Diagnosis</button>
                  ) : null}
                  <button className="btn" type="button" onClick={() => setShowDebugTrace(true)}>View Technical Trace</button>
                </div>
                {showEvidence && (
                  <div style={{ marginTop: 12 }}>
                    {(pipelineRun.executionRunId || pipelineRun.testCaseId) ? <ScreenshotTimeline runId={pipelineRun.executionRunId || pipelineRun.testCaseId} /> : null}
                    {pipelineRun.diagnosis?.failureEvidence?.domExcerpt || pipelineRun.diagnosis?.failureEvidence?.visibleTextExcerpt ? (
                      <pre className="editor" style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>
                        {String(pipelineRun.diagnosis.failureEvidence.domExcerpt || pipelineRun.diagnosis.failureEvidence.visibleTextExcerpt)}
                      </pre>
                    ) : null}
                    {humanAiDiagnosis(pipelineRun.diagnosis) ? (
                      <p className="muted" style={{ marginTop: 8 }}>{humanAiDiagnosis(pipelineRun.diagnosis)}</p>
                    ) : null}
                  </div>
                )}
              </div>
            )}

            {pipelineRun?.status === 'BLOCKED' && (pipelineRun.clarifications ?? []).length > 0 && (
              <ClarificationModal
                question={pipelineRun.clarifications[0]?.question ?? 'SmartQA needs a choice before continuing.'}
                options={(pipelineRun.clarifications[0]?.options ?? []).map((opt) => ({ id: opt, label: opt }))}
                onSelect={(option) => void onClarify(pipelineRun.clarifications[0].id, option)}
              />
            )}

            {pipelineRun?.status === 'STOPPED' && (
              <div>
                <p className="muted">Stopped. Your instructions are preserved — you can run again anytime.</p>
                <div className="toolbar">
                  <button className="btn primary" type="button" onClick={() => void onGenerateAndValidate()} disabled={busy}>Run Again</button>
                  <button className="btn" type="button" onClick={() => { setPipelineRun(null); setPhase('idle') }}>Edit Test</button>
                </div>
              </div>
            )}

            {(phase === 'pipeline-running' || pipelineEvents.length > 0) && pipelineRun?.status !== 'PASS' && pipelineRun?.status !== 'FAIL' && (
              <button className="btn" type="button" style={{ marginTop: 12 }} onClick={() => setShowTechnicalDetails((v) => !v)}>
                {showTechnicalDetails ? 'Hide technical stream' : 'View Technical Details'}
              </button>
            )}
            {showTechnicalDetails && pipelineEvents.length > 0 && (
              <pre className="log-block">{pipelineEvents.map((e) => `${eventLabel(e.type)}: ${e.message}`).join('\n')}</pre>
            )}
          </article>
        )}

        {/* STEP 2: REVIEW */}
        {wizardStep === 'review' && intent && (
          <article className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
              <span style={{ color: 'var(--ok)', fontSize: 20 }}>✓</span>
              <h2 style={{ fontSize: 20, margin: 0 }}>Test ready</h2>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 12, marginBottom: 16 }}>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Scenario</div>
                <div style={{ fontWeight: 600 }}>{intent.scenarios?.[0]?.name ?? testCase?.name ?? 'Test'}</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Actions</div>
                <div style={{ fontWeight: 600, fontSize: 20 }}>{totalSteps}</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Assertions</div>
                <div style={{ fontWeight: 600, fontSize: 20 }}>{assertionCount}</div>
              </div>
              {intent.confidence != null && (
                <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                  <div className="muted" style={{ fontSize: 12 }}>Confidence</div>
                  <div style={{ fontWeight: 600, fontSize: 20 }}>{Math.round(intent.confidence * 100)}%</div>
                </div>
              )}
            </div>

            <ol className="step-list" style={{ marginBottom: 16 }}>
              {intent.scenarios?.flatMap((s) => s.steps ?? []).map((step) => (
                <li key={step.id} style={{ padding: '4px 0' }}>
                  <span>{step.action} {step.target ?? ''} {step.value ? `"${step.value}"` : ''}</span>
                  {step.assertion && <span className="muted" style={{ marginLeft: 8 }}>→ {step.assertion}</span>}
                </li>
              ))}
            </ol>

            {/* Clarifications */}
            {intent.clarifications?.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <h3 style={{ fontSize: 14 }}>Clarifications needed</h3>
                {intent.clarifications.map((q) => (
                  <div key={q.id} style={{ marginBottom: 8 }}>
                    <p>{q.question}</p>
                    <div className="toolbar">
                      {q.options.map((opt) => (
                        <button key={opt} className="btn" type="button" onClick={() => void onClarify(q.id, opt)}>{opt}</button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}

            <div className="toolbar">
              <button className="btn" type="button" onClick={goBack}>Back</button>
              <button className="btn" type="button" onClick={() => void onSave()} disabled={busy}>Save</button>
              <button className="btn primary" type="button" onClick={() => void onGenerate()} disabled={!canGenerate}>
                Generate Test
              </button>
            </div>
          </article>
        )}

        {/* STEP 3: GENERATE (Progress) */}
        {wizardStep === 'generate' && (
          <article className="card">
            <h2 style={{ fontSize: 20, marginBottom: 16 }}>
              {phase === 'failed' ? 'Generation failed' : 'Generating your test...'}
            </h2>

            {phase !== 'failed' && (
              <p className="muted" style={{ marginBottom: 16 }}>
                SmartQA is inspecting the application and creating the automation.
              </p>
            )}

            {/* Current step indicator */}
            {generationEvents.length > 0 && (
              <p style={{ marginBottom: 12 }}>
                Current: <strong>{eventLabel(generationEvents[generationEvents.length - 1].type)}</strong>
              </p>
            )}

            <ul className="progress-list" style={{ marginBottom: 16 }}>
              {generationProgress.map((step) => (
                <li key={step.key} className={step.status === 'completed' ? 'done' : step.status === 'running' ? 'current' : step.status === 'failed' ? 'failed' : ''}>
                  <span style={{ marginRight: 8 }}>
                    {step.status === 'completed' ? '✓' : step.status === 'running' ? '●' : step.status === 'failed' ? '✗' : '○'}
                  </span>
                  {step.label}
                </li>
              ))}
            </ul>

            {/* Error in generation */}
            {phase === 'failed' && error && (
              <div className="failure-box">
                <p className="error-text" style={{ fontWeight: 600 }}>✗ Generation could not complete</p>
                <p style={{ margin: '8px 0' }}>
                  <strong>What happened?</strong><br />
                  {error}
                </p>
                {generationRun?.durationMs != null && (
                  <p className="muted" style={{ fontSize: 12 }}>Duration: {Math.round(generationRun.durationMs / 1000)}s</p>
                )}
                <p className="muted" style={{ fontSize: 12, marginTop: 4 }}>Suggested action: Check the generation service and retry.</p>
                <div className="toolbar" style={{ marginTop: 12 }}>
                  <button className="btn primary" type="button" onClick={() => void onGenerate()}>Retry Generation</button>
                  <button className="btn" type="button" onClick={() => setShowDebugTrace(true)}>View Debug Trace</button>
                </div>
                {errorDetails && (
                  <>
                    <button className="btn" type="button" onClick={() => setShowTechnicalDetails((v) => !v)} style={{ marginTop: 8, fontSize: 12 }}>
                      {showTechnicalDetails ? 'Hide technical details' : 'Technical details'}
                    </button>
                    {showTechnicalDetails && <pre className="log-block">{errorDetails}</pre>}
                  </>
                )}
              </div>
            )}

            {/* Show technical details expandable */}
            {phase === 'generating' && (
              <button className="btn" type="button" onClick={() => setShowTechnicalDetails((v) => !v)} style={{ fontSize: 12 }}>
                {showTechnicalDetails ? 'Hide technical details' : 'Show technical details'}
              </button>
            )}
            {showTechnicalDetails && phase === 'generating' && (
              <div className="trace-live" style={{ marginTop: 8, maxHeight: 200 }}>
                {generationEvents.map((evt, i) => (
                  <div key={i} style={{ padding: '2px 0', borderBottom: '1px solid #1d2430' }}>
                    <span className="muted" style={{ marginRight: 8 }}>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                    <span>{eventLabel(evt.type)}</span>
                    {evt.message && <span className="muted" style={{ marginLeft: 8 }}>— {evt.message}</span>}
                  </div>
                ))}
              </div>
            )}
          </article>
        )}

        {/* STEP 4: TEST READY */}
        {wizardStep === 'result' && (
          <article className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
              <span style={{ color: 'var(--ok)', fontSize: 24 }}>✓</span>
              <h2 style={{ fontSize: 20, margin: 0 }}>Test generated successfully</h2>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 12, marginBottom: 16 }}>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Scenario</div>
                <div style={{ fontWeight: 600 }}>{intent?.scenarios?.[0]?.name ?? testCase?.name ?? 'Test'}</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Actions</div>
                <div style={{ fontWeight: 600, fontSize: 20 }}>{totalSteps}</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Assertions</div>
                <div style={{ fontWeight: 600, fontSize: 20 }}>{assertionCount}</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Generation</div>
                <div style={{ fontWeight: 600, color: 'var(--ok)' }}>PASSED</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Quality Gate</div>
                <div style={{ fontWeight: 600, color: 'var(--ok)' }}>PASSED</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Execution</div>
                <div className="muted">NOT RUN</div>
              </div>
            </div>

            <div className="toolbar" style={{ marginBottom: 16 }}>
              <button className="btn primary" type="button" onClick={() => void onValidate()} disabled={validating || !canExecute}>
                {validating ? 'Validating…' : 'Validate'}
              </button>
              <button className="btn" type="button" onClick={() => void onExecute()} disabled={!canExecute}>
                Execute Test
              </button>
              <button className="btn" type="button" onClick={() => setShowGeneratedCode((v) => !v)}>
                {showGeneratedCode ? 'Hide Generated Test' : 'View Generated Test'}
              </button>
              <button className="btn" type="button" onClick={() => setShowDebugTrace((v) => !v)}>
                Debug Trace
              </button>
            </div>

            {showGeneratedCode && (
              <div style={{ marginBottom: 16 }}>
                <textarea className="editor code-editor" rows={14} value={code} onChange={(e) => setCode(e.target.value)} />
                <div className="toolbar" style={{ marginTop: 8 }}>
                  <button className="btn" type="button" onClick={onCopy} disabled={!code}>Copy</button>
                  <button className="btn" type="button" onClick={onDownload} disabled={!code}>Download</button>
                  <button className="btn" type="button" onClick={() => void onGenerate()} disabled={!canGenerate}>Regenerate</button>
                </div>
              </div>
            )}

            <button className="btn" type="button" onClick={goBack} style={{ fontSize: 12 }}>Back to review</button>
          </article>
        )}

        {/* EXECUTION IN PROGRESS */}
        {wizardStep === 'execute' && (
          <article className="card">
            <h2 style={{ fontSize: 20, marginBottom: 4 }}>Running test</h2>
            <p className="muted" style={{ marginBottom: 16 }}>{testCase?.name ?? 'Test'}</p>

            <CurrentAction
              events={executionEvents}
              onStop={() => void onStop()}
              stopping={stopping}
            />

            <div style={{ marginTop: 12 }}>
              <StepTimeline events={executionEvents} />
            </div>

            {run?.id ? (
              <div style={{ marginTop: 12 }}>
                <ScreenshotTimeline runId={run.id} />
              </div>
            ) : null}

            <button className="btn" type="button" onClick={() => setShowTechnicalDetails((v) => !v)} style={{ fontSize: 12, marginTop: 12 }}>
              {showTechnicalDetails ? 'Hide details' : 'View Technical Details'}
            </button>
            {showTechnicalDetails && (
              <div className="trace-live" style={{ marginTop: 8, maxHeight: 200 }}>
                {executionEvents.map((evt, i) => (
                  <div key={i} style={{ padding: '2px 0', borderBottom: '1px solid #1d2430' }}>
                    <span className="muted" style={{ marginRight: 8 }}>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                    <span>{eventLabel(evt.type)}</span>
                    {evt.message && <span className="muted" style={{ marginLeft: 8 }}>— {evt.message}</span>}
                  </div>
                ))}
              </div>
            )}
          </article>
        )}

        {/* VALIDATION IN PROGRESS */}
        {wizardStep === 'validate' && (
          <article className="card">
            <h2 style={{ fontSize: 20, marginBottom: 8 }}>Validating generated test</h2>
            <p className="muted">
              SmartQA is running independent browser validation before execution.
            </p>
          </article>
        )}

        {/* EXECUTION RESULT */}
        {wizardStep === 'execution-result' && (
          <article className="card">
            {executionPassed ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
                  <span style={{ color: 'var(--ok)', fontSize: 28 }}>✓</span>
                  <h2 style={{ fontSize: 22, margin: 0, color: 'var(--ok)' }}>Test Passed</h2>
                </div>
                <p style={{ marginBottom: 16 }}>{executionStepCount} / {totalSteps} steps completed</p>
                {assertionCount > 0 && <p style={{ color: 'var(--ok)' }}>✓ All assertions passed</p>}
              </>
            ) : phase === 'stopped' ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
                  <span style={{ fontSize: 28, color: 'var(--muted)' }}>⏹</span>
                  <h2 style={{ fontSize: 22, margin: 0 }}>Test Stopped</h2>
                </div>
                <p className="muted">{executionStepCount} / {totalSteps} steps completed before stop.</p>
              </>
            ) : (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
                  <span style={{ color: 'var(--danger)', fontSize: 28 }}>✗</span>
                  <h2 style={{ fontSize: 22, margin: 0, color: 'var(--danger)' }}>Test Failed</h2>
                </div>
                <p>Step: {executionStepCount + 1} of {totalSteps}</p>
                {run?.errorMessage && (
                  <p style={{ marginTop: 8 }}>
                    <strong>Reason:</strong> {run.errorMessage}
                  </p>
                )}
                {error && !run?.errorMessage && <p className="error-text">{error}</p>}
              </>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 12, margin: '16px 0' }}>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Generation</div>
                <div style={{ fontWeight: 600, color: 'var(--ok)' }}>PASSED</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Quality Gate</div>
                <div style={{ fontWeight: 600, color: 'var(--ok)' }}>PASSED</div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Execution</div>
                <div style={{ fontWeight: 600, color: executionPassed ? 'var(--ok)' : phase === 'stopped' ? 'var(--muted)' : 'var(--danger)' }}>
                  {executionPassed ? 'PASSED' : phase === 'stopped' ? 'STOPPED' : 'FAILED'}
                </div>
              </div>
              <div className="card" style={{ padding: '12px', textAlign: 'center' }}>
                <div className="muted" style={{ fontSize: 12 }}>Validation</div>
                <div className={validationRun?.status === 'PASSED' ? 'ok-text' : validationRun?.status === 'FAILED' ? 'error-text' : 'muted'}>
                  {validationRun?.status ?? 'NOT RUN'}
                </div>
              </div>
            </div>

            <div className="toolbar">
              <button className="btn primary" type="button" onClick={() => void onExecute()} disabled={!canExecute}>
                Run Again
              </button>
              <button className="btn" type="button" onClick={() => void onValidate()} disabled={validating || !canExecute}>
                {validating ? 'Validating…' : 'Validate'}
              </button>
              <button className="btn" type="button" onClick={() => setShowTechnicalDetails((v) => !v)}>
                View Details
              </button>
              <button className="btn" type="button" onClick={() => setShowDebugTrace((v) => !v)}>
                Debug Trace
              </button>
            </div>

            {showTechnicalDetails && (
              <div style={{ marginTop: 16 }}>
                {run?.stdout && (
                  <div style={{ marginBottom: 12 }}>
                    <h4 style={{ fontSize: 13, color: 'var(--muted)' }}>Standard Output</h4>
                    <pre className="log-block">{run.stdout}</pre>
                  </div>
                )}
                {run?.stderr && (
                  <div>
                    <h4 style={{ fontSize: 13, color: 'var(--muted)' }}>Error Output</h4>
                    <pre className="log-block">{run.stderr}</pre>
                  </div>
                )}
                {/* Locator Evidence */}
                {locatorMemory.length > 0 && (
                  <div style={{ marginTop: 16 }}>
                    <h4 style={{ fontSize: 13, color: 'var(--muted)' }}>Locator Evidence</h4>
                    <table className="table" style={{ fontSize: 12 }}>
                      <thead>
                        <tr>
                          <th>Target</th>
                          <th>Action</th>
                          <th>Locator</th>
                          <th>Confidence</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {locatorMemory.map((entry, i) => (
                          <tr key={i}>
                            <td>{entry.semanticTarget ?? '—'}</td>
                            <td>{entry.action}</td>
                            <td style={{ fontFamily: 'monospace', fontSize: 11 }}>{formatLocator(entry.locatorType, entry.resolvedLocator)}</td>
                            <td>{Math.round(entry.confidence * 100)}%</td>
                            <td>
                              {entry.confidence >= 0.9 ? (
                                <span className="ok-text">✓ Verified</span>
                              ) : entry.confidence >= 0.7 ? (
                                <span style={{ color: '#f0c674' }}>⚠ Needs review</span>
                              ) : (
                                <span className="error-text">✗ Low confidence</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <div className="trace-live" style={{ marginTop: 12, maxHeight: 200 }}>
                  {executionEvents.map((evt, i) => (
                    <div key={i} style={{ padding: '2px 0', borderBottom: '1px solid #1d2430' }}>
                      <span className="muted" style={{ marginRight: 8 }}>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                      <span>{eventLabel(evt.type)}</span>
                      {evt.message && <span className="muted" style={{ marginLeft: 8 }}>— {evt.message}</span>}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </article>
        )}
      </div>

      {/* DEBUG TRACE */}
      {runtimeClarification ? (
        <ClarificationModal
          question={runtimeClarification.question}
          options={runtimeClarification.options}
          onSelect={(id) => {
            const clarificationId = runtimeClarification.id
            setRuntimeClarification(null)
            void resolveRuntimeClarification(clarificationId, id)
          }}
        />
      ) : null}

      {showDebugTrace && (
        <div id="debug-trace" style={{ marginTop: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <h2 style={{ margin: 0 }}>Debug Trace</h2>
            <button className="btn" type="button" onClick={() => setShowDebugTrace(false)} style={{ fontSize: 12 }}>Close</button>
          </div>
          <DebugTracePanel running={busy} />
        </div>
      )}
    </section>
  )
}

async function pollRun(id: string) {
  for (let attempt = 0; attempt < 90; attempt += 1) {
    const current = await getExecutionRun(id)
    if (current.status !== 'RUNNING') return current
    await new Promise((resolve) => window.setTimeout(resolve, 2000))
  }
  return getExecutionRun(id)
}

async function pollValidation(id: string) {
  for (let attempt = 0; attempt < 90; attempt += 1) {
    const current = await getValidationRun(id)
    if (current.status !== 'RUNNING') return current
    await new Promise((resolve) => window.setTimeout(resolve, 2000))
  }
  return getValidationRun(id)
}
