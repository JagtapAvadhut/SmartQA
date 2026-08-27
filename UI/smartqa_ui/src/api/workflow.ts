import { apiData } from './client'
import type { Project } from '../types/project'
import type { TestCase } from '../types/testcase'
import type { ExecutionRun, ValidationRun } from '../types/intent'

export interface GenerationRun {
  id: string
  testCaseId: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'STOPPED'
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
  errorMessage: string | null
  failedStep?: string | null
  result?: string | null
}

export function understandTestCase(id: string) {
  return apiData<TestCase>(`/api/test-cases/${id}/understand`, { method: 'POST' })
}

export function analyzeWorkspace(request: {
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
}) {
  return apiData<{ project: Project; testCase: TestCase }>('/api/workspace/analyze', {
    method: 'POST',
    body: JSON.stringify({
      applicationUrl: request.applicationUrl,
      instructions: request.instructions,
      projectId: request.projectId || null,
      testCaseId: request.testCaseId || null,
      structuredSteps: request.structuredSteps ?? null,
    }),
  })
}

export function clarifyTestCase(
  id: string,
  answers: { questionId: string; selectedOption: string }[],
) {
  return apiData<TestCase>(`/api/test-cases/${id}/clarify`, {
    method: 'POST',
    body: JSON.stringify({ answers }),
  })
}

export function acceptTestCase(id: string) {
  return apiData<TestCase>(`/api/test-cases/${id}/accept`, { method: 'POST' })
}

export function startGeneration(id: string) {
  return apiData<GenerationRun>(`/api/test-cases/${id}/generate`, { method: 'POST' })
}

export function getGenerationRun(runId: string) {
  return apiData<GenerationRun>(`/api/generation-runs/${runId}`)
}

export function getLatestGenerationRun(testCaseId: string) {
  return apiData<GenerationRun>(`/api/test-cases/${testCaseId}/generation/latest`)
}

/** @deprecated use startGeneration for async flow */
export function generateTestCase(id: string) {
  return startGeneration(id)
}

export function saveGeneratedCode(id: string, generatedCode: string) {
  return apiData<TestCase>(`/api/test-cases/${id}/code`, {
    method: 'PUT',
    body: JSON.stringify({ generatedCode }),
  })
}

export function executeTestCase(id: string) {
  return apiData<ExecutionRun>(`/api/test-cases/${id}/execute`, { method: 'POST' })
}

export function executeTestCaseWithOptions(
  id: string,
  options: { executionProvider: string; browserMode: 'headed' | 'headless'; headless: boolean },
) {
  return apiData<ExecutionRun>(`/api/test-cases/${id}/execute`, {
    method: 'POST',
    body: JSON.stringify(options),
  })
}

export function getExecutionRun(id: string) {
  return apiData<ExecutionRun>(`/api/execution-runs/${id}`)
}

export function stopExecutionRun(id: string) {
  return apiData<ExecutionRun>(`/api/execution-runs/${id}/stop`, { method: 'POST' })
}

export function validateTestCase(id: string) {
  return apiData<ValidationRun>(`/api/test-cases/${id}/validate`, { method: 'POST' })
}

export function getValidationRuns(id: string) {
  return apiData<ValidationRun[]>(`/api/test-cases/${id}/validations`)
}

export function getValidationRun(id: string) {
  return apiData<ValidationRun>(`/api/validation-runs/${id}`)
}

export function checkGenerationHealth() {
  return apiData<{ status: string; generationAvailable: boolean }>('/api/health/generation')
}
