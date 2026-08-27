import { apiData } from './client'

export interface RuntimeClarificationCandidate {
  candidateId?: string
  label?: string
  score?: number
  context?: string
}

export interface RuntimeClarification {
  id: string
  testCaseId: string | null
  executionRunId: string | null
  stepId: string | null
  target: string | null
  reason: string | null
  question: string
  candidates: RuntimeClarificationCandidate[]
  selectedCandidateId: string | null
  status: string
}

export function getRuntimeClarification(id: string) {
  return apiData<RuntimeClarification>(`/api/runtime-clarifications/${id}`)
}

export function resolveRuntimeClarification(id: string, selectedCandidateId: string) {
  return apiData<RuntimeClarification>(`/api/runtime-clarifications/${id}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ selectedCandidateId, selectedOption: selectedCandidateId }),
  })
}
