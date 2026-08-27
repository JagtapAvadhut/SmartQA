import { apiData } from './client'
import type { TestCase, TestCaseRequest } from '../types/testcase'

export function listTestCases(projectId: string) {
  return apiData<TestCase[]>(`/api/projects/${projectId}/test-cases`)
}

export function getTestCase(id: string) {
  return apiData<TestCase>(`/api/test-cases/${id}`)
}

export function createTestCase(projectId: string, request: TestCaseRequest) {
  return apiData<TestCase>(`/api/projects/${projectId}/test-cases`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateTestCase(id: string, request: TestCaseRequest) {
  return apiData<TestCase>(`/api/test-cases/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteTestCase(id: string) {
  return apiData<null>(`/api/test-cases/${id}`, { method: 'DELETE' })
}
