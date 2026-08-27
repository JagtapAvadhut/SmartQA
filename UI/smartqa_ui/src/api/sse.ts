import { traceLogger } from '../services/traceLogger'
import type { ProgressEvent } from '../types/intent'

export type SseHandlers = {
  onEvent: (eventName: string, data: unknown) => void
  onError?: (error: Event) => void
  onOpen?: () => void
}

export type SseConnectOptions = {
  handlers: SseHandlers
  /** Identifies the stream for logging and recovery (testCaseId or executionRunId). */
  streamKey?: string
  generationRunId?: string | null
  pipelineRunId?: string
  /** Called after reconnect to recover missed terminal state. */
  recoverState?: () => Promise<void>
  maxReconnectAttempts?: number
}

const DEFAULT_MAX_RECONNECT = 5
const INITIAL_RECONNECT_DELAY_MS = 1_000
const MAX_RECONNECT_DELAY_MS = 8_000

const NAMED_EVENTS = [
  'GENERATION_STARTED',
  'INTENT_ANALYZED',
  'INTENT_READY',
  'AI_PROVIDER_SELECTED',
  'AI_FALLBACK_STARTED',
  'AI_FALLBACK_SUCCEEDED',
  'ERROR',
  'CLARIFICATION_REQUIRED',
  'BROWSER_STARTED',
  'PAGE_LOADED',
  'DOM_FETCHED',
  'ELEMENT_DISCOVERED',
  'LOCATOR_SELECTED',
  'LOCATOR_RESOLVED',
  'FILTER_APPLIED',
  'STATE_CHANGED',
  'STEP_STARTED',
  'STEP_COMPLETED',
  'CODE_GENERATING',
  'CODE_GENERATED',
  'QUALITY_GATE_STARTED',
  'QUALITY_GATE_PASSED',
  'GENERATION_COMPLETE',
  'GENERATION_ERROR',
  'EXECUTION_STARTED',
  'SCENARIO_STARTED',
  'STEP_PASSED',
  'STEP_FAILED',
  'HEALING_STARTED',
  'HEALING_SUCCESS',
  'HEALING_COMPLETED',
  'SCREENSHOT_CAPTURED',
  'EXECUTION_COMPLETED',
  'EXECUTION_FAILED',
  'EXECUTION_STOP_REQUESTED',
  'EXECUTION_STOPPED',
  'ACTION_STARTED',
  'ACTION_COMPLETED',
  'ACTION_FAILED',
  'CONTROL_CLASSIFIED',
  'LOCATOR_VERIFIED',
  'ASSERTION_VERIFIED',
  'ASSERTION_FAILED',
  'VALIDATION_STARTED',
  'VALIDATION_STEP_STARTED',
  'VALIDATION_STEP_PASSED',
  'VALIDATION_STEP_FAILED',
  'VALIDATION_COMPLETED',
  'MCP_REQUEST_STARTED',
  'MCP_REQUEST_COMPLETED',
  'CDP_EVENT',
  'QUALITY_GATE_FAILED',
  'PIPELINE_STARTED',
  'PIPELINE_STAGE',
  'PIPELINE_RETRY',
  'PIPELINE_PASSED',
  'PIPELINE_FAILED',
  'PIPELINE_BLOCKED',
  'PIPELINE_STOPPED',
  'PIPELINE_STAGE_FAILED',
  'WAITING_FOR_CLARIFICATION',
  'CLARIFICATION_RESOLVED',
] as const

export function connectSse(path: string, handlersOrOptions: SseHandlers | SseConnectOptions): () => void {
  const options = normalizeOptions(handlersOrOptions)
  const { handlers, streamKey, generationRunId, recoverState, pipelineRunId } = options
  const maxAttempts = options.maxReconnectAttempts ?? DEFAULT_MAX_RECONNECT

  let closed = false
  let reconnectAttempts = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let source: EventSource | null = null
  const seenEventIds = new Set<string>()
  const connectionId = `${streamKey ?? path}-${Date.now()}`
  const lastEventStorageKey = streamKey ? `smartqa.sse.lastEventId.${streamKey}` : null
  let lastEventId: string | null = lastEventStorageKey ? sessionStorage.getItem(lastEventStorageKey) : null

  const buildUrl = () => {
    const params = new URLSearchParams()
    params.set('traceId', traceLogger.getTraceId())
    if (generationRunId) {
      params.set('generationRunId', generationRunId)
    }
    if (pipelineRunId) {
      params.set('pipelineRunId', pipelineRunId)
    }
    if (lastEventId) {
      params.set('lastEventId', lastEventId)
    }
    const separator = path.includes('?') ? '&' : '?'
    return `${path}${separator}${params.toString()}`
  }

  const dispatchEvent = (eventName: string, data: unknown, rawEvent?: MessageEvent) => {
    const eventId = extractEventId(data, rawEvent)
    const eventGenerationRunId = extractCorrelationId(data, 'generationRunId') ?? generationRunId
    const eventPipelineRunId = extractCorrelationId(data, 'pipelineRunId')
    if (pipelineRunId && eventPipelineRunId && eventPipelineRunId !== pipelineRunId) {
      traceLogger.info('SSE', 'SSE_EVENT_PIPELINE_MISMATCH_IGNORED', 'Ignored SSE event for another pipeline run', {
        connectionId,
        pipelineRunId,
        eventPipelineRunId,
        eventId,
        eventType: eventName,
      })
      return
    }
    if (generationRunId && eventGenerationRunId && eventGenerationRunId !== generationRunId) {
      traceLogger.info('SSE', 'SSE_EVENT_RUN_MISMATCH_IGNORED', 'Ignored SSE event for another generation run', {
        connectionId,
        generationRunId: generationRunId ?? null,
        eventGenerationRunId,
        eventId,
        eventType: eventName,
      })
      return
    }
    if (eventId) {
      if (seenEventIds.has(eventId)) {
        traceLogger.info('SSE', 'SSE_EVENT_DUPLICATE_IGNORED', 'Ignored duplicate SSE event', {
          connectionId,
          generationRunId: eventGenerationRunId ?? null,
          pipelineRunId: eventPipelineRunId ?? null,
          eventId,
          eventType: eventName,
        })
        return
      }
      seenEventIds.add(eventId)
      lastEventId = eventId
      if (lastEventStorageKey) {
        sessionStorage.setItem(lastEventStorageKey, eventId)
      }
    }
    traceLogger.info('SSE', 'SSE_EVENT_RECEIVED', eventName, {
      connectionId,
      generationRunId: eventGenerationRunId ?? null,
      pipelineRunId: eventPipelineRunId ?? null,
      eventId,
      eventType: eventName,
    })
    handlers.onEvent(eventName, data)
  }

  const attachListeners = (active: EventSource) => {
    active.onopen = () => {
      if (reconnectAttempts > 0) {
        traceLogger.info('SSE', 'SSE_RECONNECTED', 'SSE connection re-established', {
          connectionId,
          generationRunId: generationRunId ?? null,
          reconnectAttempt: reconnectAttempts,
        })
      } else {
        traceLogger.info('SSE', 'SSE_CONNECTED', 'SSE connection opened', {
          connectionId,
          url: path,
          generationRunId: generationRunId ?? null,
        })
      }
      reconnectAttempts = 0
      handlers.onOpen?.()
      if (recoverState) {
        void recoverState().catch(() => undefined)
      }
    }

    active.onerror = (event) => {
      if (closed) {
        return
      }
      traceLogger.error('SSE', 'SSE_DISCONNECTED', 'SSE connection error', {
        connectionId,
        url: path,
        generationRunId: generationRunId ?? null,
        reconnectAttempt: reconnectAttempts,
      }, event)
      handlers.onError?.(event)
      active.close()
      source = null
      scheduleReconnect()
    }

    active.onmessage = (event) => {
      dispatchEvent(event.type || 'message', parse(event.data), event)
    }

    for (const name of NAMED_EVENTS) {
      active.addEventListener(name, (event) => {
        const message = event as MessageEvent
        dispatchEvent(name, parse(message.data), message)
      })
    }
  }

  const scheduleReconnect = () => {
    if (closed || reconnectAttempts >= maxAttempts) {
      if (!closed && reconnectAttempts >= maxAttempts) {
        traceLogger.error('SSE', 'SSE_RECONNECT_FAILED', 'SSE reconnect attempts exhausted', {
          connectionId,
          generationRunId: generationRunId ?? null,
          reconnectAttempt: reconnectAttempts,
        })
      }
      return
    }
    reconnectAttempts += 1
    const delay = Math.min(
      INITIAL_RECONNECT_DELAY_MS * Math.pow(2, reconnectAttempts - 1),
      MAX_RECONNECT_DELAY_MS,
    )
    traceLogger.info('SSE', 'SSE_RECONNECT_STARTED', 'Scheduling SSE reconnect', {
      connectionId,
      generationRunId: generationRunId ?? null,
      reconnectAttempt: reconnectAttempts,
      reconnectDelayMs: delay,
    })
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      if (closed) {
        return
      }
      traceLogger.info('SSE', 'SSE_CONNECTED', 'Opening SSE connection', {
        connectionId,
        url: path,
        generationRunId: generationRunId ?? null,
        reconnectAttempt: reconnectAttempts,
      })
      source = new EventSource(buildUrl())
      attachListeners(source)
    }, delay)
  }

  traceLogger.info('SSE', 'SSE_CONNECTED', 'Opening SSE connection', {
    connectionId,
    url: path,
    generationRunId: generationRunId ?? null,
  })
  source = new EventSource(buildUrl())
  attachListeners(source)

  return () => {
    closed = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    traceLogger.info('SSE', 'SSE_DISCONNECTED', 'SSE connection closed', {
      connectionId,
      url: path,
      generationRunId: generationRunId ?? null,
    })
    source?.close()
    source = null
  }
}

function normalizeOptions(handlersOrOptions: SseHandlers | SseConnectOptions): SseConnectOptions {
  if ('handlers' in handlersOrOptions) {
    return handlersOrOptions
  }
  return { handlers: handlersOrOptions }
}

function extractCorrelationId(data: unknown, key: 'generationRunId' | 'pipelineRunId'): string | undefined {
  if (!data || typeof data !== 'object') {
    return undefined
  }
  const record = data as ProgressEvent & Record<string, unknown>
  const direct = record[key]
  if (typeof direct === 'string' && direct.length > 0) {
    return direct
  }
  const details = record.details
  if (details && typeof details === 'object') {
    const nested = details[key]
    if (typeof nested === 'string' && nested.length > 0) {
      return nested
    }
  }
  return undefined
}

function extractEventId(data: unknown, rawEvent?: MessageEvent): string | null {
  if (rawEvent?.lastEventId) {
    return rawEvent.lastEventId
  }
  if (data && typeof data === 'object' && 'eventId' in data) {
    const eventId = (data as ProgressEvent).eventId
    if (eventId != null) {
      return String(eventId)
    }
  }
  return null
}

function parse(data: string): unknown {
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}
