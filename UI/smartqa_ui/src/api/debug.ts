import { apiRequest } from './client'
import type { ApiResponse } from './client'
import type { TraceEvent } from '../services/traceLogger'

export interface TraceResponse {
  traceId: string
  status: string
  events: TraceEvent[]
  eventCount: number
  errors: number
}

export function getTrace(traceId: string) {
  return apiRequest<ApiResponse<TraceResponse>>(`/api/debug/traces/${encodeURIComponent(traceId)}`)
}

export function downloadTraceUrl(traceId: string, format: 'log' | 'jsonl' = 'log') {
  return `/api/debug/traces/${encodeURIComponent(traceId)}/download?format=${format}`
}
