import { apiData } from './client'
import type { TestCase } from '../types/testcase'

export interface RecoveryOption {
  type: string
  reason: string
  safe: boolean
  confidence?: number
  targetHint?: string
  domainHint?: string
}

export interface AiDiagnosticResult {
  classification: string
  rootCause: string
  confidence: number
  explanation: string
  recoveryOptions?: RecoveryOption[]
  requiresUserInput?: boolean
  requiresSourceFix?: boolean
  userQuestion?: string
  userOptions?: string[]
  assertionSubCategory?: string
  searchSubCategory?: string
  filterSubCategory?: string
  responsibleSubsystem?: string
}

export interface FailureEvidence {
  url?: string
  pageTitle?: string
  expected?: string
  actual?: string
  failureCategory?: string
  screenshotPath?: string
  visibleTextExcerpt?: string
  domExcerpt?: string
  accessibilityExcerpt?: string
  stepNumber?: number
  action?: string
  target?: string
}

export interface SourceFixProposal {
  id: string
  component: string
  className: string
  method: string
  rootCause: string
  evidence: string
  affectedTests?: string[]
  recommendedChange: string
  regressionTest: string
  status: string
  applied?: boolean
}

export interface FailureDiagnosis {
  whatFailed: string
  whyFailed: string
  responsibleComponent: string
  category: string
  evidence: string
  autoHealAttempted: boolean
  attemptsUsed: number
  recommendedAction: string
  candidateHints?: string[]
  details?: Record<string, unknown>
  failureEvidence?: FailureEvidence | null
  aiDiagnosis?: AiDiagnosticResult | null
  recoveryAttempts?: string[]
  sourceFix?: SourceFixProposal | null
  rootCause?: string | null
  aiConfidence?: number | null
  requiresSourceFix?: boolean
  autoRecoveryAttempted?: boolean
  autoRecoverySucceeded?: boolean
  screenshotPath?: string | null
}

export interface PipelineRun {
  id: string
  status: 'QUEUED' | 'RUNNING' | 'PASS' | 'VALIDATED_NOT_EXECUTED' | 'FAIL' | 'BLOCKED' | 'STOPPED' | 'ABANDONED'
  stage: string
  userStageLabel: string
  projectId: string | null
  testCaseId: string | null
  generationRunId: string | null
  validationRunId: string | null
  executionRunId: string | null
  applicationUrl: string | null
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
  errorMessage: string | null
  finalSummary: string | null
  diagnosis: FailureDiagnosis | null
  attempt: number
  maxAttempts: number
  userProgress: string[]
  details: Record<string, unknown>
  testCase: TestCase | null
  clarifications: Array<{ id: string; question: string; options: string[] }>
}

export function startGenerateAndValidate(request: {
  applicationUrl: string
  instructions: string
  projectId?: string | null
  testCaseId?: string | null
  structuredSteps?: Array<{
    id: string
    action: string
    target: string | null
    value: string | null
    assertion: string | null
    location: string | null
    filter?: unknown
  }> | null
  browserMode?: 'headed' | 'headless'
  headless?: boolean
  skipExecution?: boolean
  maxAttempts?: number
}) {
  return apiData<PipelineRun>('/api/workspace/generate-and-validate', {
    method: 'POST',
    body: JSON.stringify({
      applicationUrl: request.applicationUrl,
      instructions: request.instructions,
      projectId: request.projectId || null,
      testCaseId: request.testCaseId || null,
      structuredSteps: request.structuredSteps ?? null,
      browserMode: request.browserMode ?? 'headed',
      headless: request.headless ?? false,
      skipExecution: request.skipExecution ?? false,
      maxAttempts: request.maxAttempts ?? 3,
    }),
  })
}

export function getPipelineRun(id: string) {
  return apiData<PipelineRun>(`/api/pipelines/${id}`)
}

export async function getLatestPipeline(testCaseId: string): Promise<PipelineRun | null> {
  try {
    return await apiData<PipelineRun>(`/api/test-cases/${testCaseId}/pipelines/latest`)
  } catch {
    return null
  }
}

export function stopPipelineRun(id: string) {
  return apiData<PipelineRun>(`/api/pipelines/${id}/stop`, { method: 'POST' })
}

export function requestFixAndRebuild(id: string) {
  return apiData<SourceFixProposal>(`/api/pipelines/${id}/fix-and-rebuild`, { method: 'POST' })
}
