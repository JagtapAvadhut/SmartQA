import { API_BASE_URL, describeNetworkFailure } from './client'
import { mask, maskText } from '../services/secretMasker'
import { traceLogger } from '../services/traceLogger'

export interface HealthResponse {
  status: string
  application: string
}

export interface HealthCheckResult {
  ok: boolean
  status: string
  application: string | null
  url: string
  durationMs: number
  httpStatus: number | null
  body: unknown
  errorType: string | null
  errorMessage: string | null
  reason: string | null
}

export function healthUrl(): string {
  const origin = API_BASE_URL || (typeof window === 'undefined' ? '' : window.location.origin)
  return `${origin}/api/health`
}

export async function checkHealth(): Promise<HealthCheckResult> {
  const url = healthUrl()
  const started = performance.now()
  traceLogger.info('HTTP', 'HEALTH_CHECK_STARTED', 'Checking backend health', { url })
  traceLogger.info('HTTP', 'HEALTH_CHECK_REQUEST', `GET ${url}`, { method: 'GET', url })
  try {
    const response = await fetch(url, {
      headers: {
        'X-SmartQA-Trace-Id': traceLogger.getTraceId(),
      },
    })
    const durationMs = Math.round(performance.now() - started)
    const text = await response.text()
    let body: unknown = text
    try {
      body = text ? JSON.parse(text) : null
    } catch {
      body = text
    }
    const payload = body as Partial<HealthResponse> | null
    const ok = response.ok && payload?.status === 'UP'
    traceLogger.info('HTTP', 'HEALTH_CHECK_RESPONSE', 'Health response received', {
      status: response.status,
      durationMs,
      body: mask(body),
    })
    traceLogger.info('HTTP', 'HEALTH_CHECK_STATUS', ok ? 'UP' : payload?.status || 'DOWN', {
      status: response.status,
      health: payload?.status ?? null,
    })
    traceLogger.info('HTTP', 'HEALTH_CHECK_DURATION', 'Health check timing', { durationMs })
    if (!ok) {
      const reason = `Health endpoint returned HTTP ${response.status}`
      traceLogger.error('HTTP', 'HEALTH_CHECK_FAILED', reason, {
        url,
        status: response.status,
        durationMs,
        body: mask(body),
      })
      return {
        ok: false,
        status: payload?.status || 'DOWN',
        application: payload?.application ?? null,
        url,
        durationMs,
        httpStatus: response.status,
        body,
        errorType: 'HttpError',
        errorMessage: reason,
        reason,
      }
    }
    traceLogger.info('HTTP', 'HEALTH_CHECK_COMPLETED', 'Backend is reachable', {
      status: response.status,
      durationMs,
      application: payload?.application,
    })
    return {
      ok: true,
      status: payload?.status || 'UP',
      application: payload?.application ?? 'SmartQA',
      url,
      durationMs,
      httpStatus: response.status,
      body,
      errorType: null,
      errorMessage: null,
      reason: null,
    }
  } catch (error) {
    const durationMs = Math.round(performance.now() - started)
    const errorType = error instanceof Error ? error.name : 'Error'
    const errorMessage = error instanceof Error ? error.message : String(error)
    const reason = describeNetworkFailure(url, errorType, errorMessage)
    traceLogger.error(
      'HTTP',
      'HEALTH_CHECK_FAILED',
      reason,
      {
        url,
        status: 0,
        durationMs,
        errorType,
        browserError: maskText(errorMessage),
      },
      error,
    )
    return {
      ok: false,
      status: 'DOWN',
      application: null,
      url,
      durationMs,
      httpStatus: null,
      body: null,
      errorType,
      errorMessage,
      reason,
    }
  }
}
