import { apiData, resolveApiUrl } from './client'

export interface ScreenshotMeta {
  id: string
  traceId: string
  executionRunId: string | null
  stepId: string
  stepNumber: number
  eventType: string
  timestamp: string
  url: string
  filePath: string
  evidenceMomentId?: string | null
}

export function getScreenshots(runId: string) {
  return apiData<ScreenshotMeta[]>(`/api/execution-runs/${runId}/screenshots`)
}

export function screenshotUrl(screenshotId: string): string {
  return resolveApiUrl(`/api/screenshots/${screenshotId}`)
}
