import { mask, maskText, summarizePayload } from '../services/secretMasker'
import { traceLogger } from '../services/traceLogger'

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') || ''

export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string | null
  readonly url: string
  readonly durationMs: number
  readonly causeType: string | null

  constructor(
    message: string,
    status: number,
    errorCode: string | null,
    url = '',
    durationMs = 0,
    causeType: string | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.url = url
    this.durationMs = durationMs
    this.causeType = causeType
  }
}

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  errorCode: string | null
  timestamp: string
}

async function parseJson(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new ApiError('Unable to parse server response', response.status, null, response.url)
  }
}

export function resolveApiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const traceId = traceLogger.getTraceId()
  headers.set('X-SmartQA-Trace-Id', traceId)

  const method = (init.method || 'GET').toUpperCase()
  const url = resolveApiUrl(path)
  const started = performance.now()
  let payload: unknown = null
  if (typeof init.body === 'string') {
    try {
      payload = JSON.parse(init.body)
    } catch {
      payload = init.body
    }
  }

  const skipTraceLog = path.startsWith('/api/debug/')
  if (!skipTraceLog) {
    traceLogger.info('HTTP', 'API_REQUEST_STARTED', `${method} ${path}`, {
      method,
      url,
      payload: summarizePayload(mask(payload)),
    })
    if (
      path.includes('generate-and-validate')
      || path.includes('generate-and-validate')
      || path.endsWith('/generate')
      || path.endsWith('/execute')
    ) {
      traceLogger.info('HTTP', 'TRACE_STARTED', `${method} ${path}`, {
        method,
        url,
        operation: path.includes('generate-and-validate') ? 'GENERATE_AND_VALIDATE' : `${method} ${path}`,
      })
    }
  }

  try {
    const response = await fetch(url, {
      ...init,
      headers,
    })
    const durationMs = Math.round(performance.now() - started)
    const backendTrace = response.headers.get('X-SmartQA-Trace-Id')
    const body = await parseJson(response)

    if (!response.ok) {
      const envelope = body as Partial<ApiResponse<unknown>> | null
      const message = envelope?.message || `Request failed (${response.status})`
      if (!skipTraceLog) {
        traceLogger.error('HTTP', 'API_REQUEST_FAILED', message, {
          method,
          url,
          status: response.status,
          durationMs,
          errorCode: envelope?.errorCode ?? null,
          response: summarizePayload(mask(body)),
          backendTraceId: backendTrace,
        })
      }
      throw new ApiError(message, response.status, envelope?.errorCode ?? null, url, durationMs)
    }

    if (!skipTraceLog) {
      traceLogger.info('HTTP', 'API_REQUEST_SUCCESS', `${method} ${path}`, {
        method,
        url,
        status: response.status,
        durationMs,
        backendTraceId: backendTrace,
        response: summarizePayload(mask(body)),
      })
    }
    return body as T
  } catch (error) {
    const durationMs = Math.round(performance.now() - started)
    if (error instanceof ApiError) {
      throw error
    }
    const causeType = error instanceof Error ? error.name : typeof error
    const causeMessage = error instanceof Error ? error.message : String(error)
    const friendly = describeNetworkFailure(url, causeType, causeMessage)
    if (!skipTraceLog) {
      traceLogger.error(
        'HTTP',
        'API_REQUEST_FAILED',
        friendly,
        {
          method,
          url,
          status: 0,
          durationMs,
          errorType: causeType,
          browserError: maskText(causeMessage),
        },
        error,
      )
    }
    throw new ApiError(friendly, 0, null, url, durationMs, causeType)
  }
}

export function describeNetworkFailure(url: string, errorType: string, message: string): string {
  const lower = `${errorType} ${message}`.toLowerCase()
  if (lower.includes('failed to fetch') || lower.includes('networkerror')) {
    return `Could not reach ${url} (${errorType}: ${message}). If the UI is on Vite, confirm the /api proxy target http://localhost:8081 is running.`
  }
  if (lower.includes('abort')) {
    return `Request to ${url} was aborted (${message}).`
  }
  return `Request to ${url} failed (${errorType}: ${message}).`
}

export async function apiData<T>(path: string, init: RequestInit = {}): Promise<T> {
  const envelope = await apiRequest<ApiResponse<T>>(path, init)
  if (!envelope.success) {
    throw new ApiError(envelope.message, 400, envelope.errorCode)
  }
  return envelope.data
}
