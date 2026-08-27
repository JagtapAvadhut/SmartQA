export type TraceLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
export type TraceComponent =
  | 'UI'
  | 'HTTP'
  | 'API'
  | 'CONTROLLER'
  | 'SERVICE'
  | 'AI'
  | 'BROWSER'
  | 'DOM'
  | 'LOCATOR'
  | 'PLAYWRIGHT'
  | 'SSE'
  | 'CODEGEN'
  | 'QUALITY_GATE'
  | 'EXECUTION'

export interface TraceEvent {
  traceId: string
  timestamp: string
  level: TraceLevel
  component: TraceComponent | string
  operation: string
  message: string
  durationMs?: number | null
  payload?: unknown
  result?: unknown
  error?: string | null
  exceptionType?: string | null
  stackTrace?: string | null
  metadata?: Record<string, unknown>
  source?: 'ui' | 'backend'
}

const TRACE_ID_KEY = 'smartqa.traceId'
const TRACE_EVENTS_KEY = 'smartqa.traceEvents'
const MAX_EVENTS = 2000

type Listener = () => void

function nowStamp(): string {
  const now = new Date()
  const pad = (value: number, size = 2) => String(value).padStart(size, '0')
  const offset = -now.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  const tz = `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}.${String(now.getMilliseconds()).padStart(3, '0')}${tz}`
}

function randomSuffix(): string {
  return Math.floor(Math.random() * 0x1000000)
    .toString(16)
    .padStart(6, '0')
}

export function createTraceId(): string {
  const now = new Date()
  const pad = (value: number, size = 2) => String(value).padStart(size, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `SMARTQA-${stamp}-${randomSuffix()}`
}

function readStoredEvents(): TraceEvent[] {
  try {
    const raw = sessionStorage.getItem(TRACE_EVENTS_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw) as TraceEvent[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

class TraceStore {
  private events: TraceEvent[] = readStoredEvents()
  private listeners = new Set<Listener>()
  private traceId: string
  private pending: TraceEvent[] = []
  private flushTimer: number | null = null

  constructor() {
    const stored = sessionStorage.getItem(TRACE_ID_KEY)
    this.traceId = stored && stored.startsWith('SMARTQA-') ? stored : createTraceId()
    sessionStorage.setItem(TRACE_ID_KEY, this.traceId)
  }

  getTraceId(): string {
    return this.traceId
  }

  start(operation: string, metadata: Record<string, unknown> = {}): string {
    this.traceId = createTraceId()
    this.events = []
    this.pending = []
    sessionStorage.setItem(TRACE_ID_KEY, this.traceId)
    this.persist()
    this.info('UI', 'TRACE_STARTED', operation, {
      operation,
      frontendVersion: '0.0.0',
      ...metadata,
    })
    this.notify()
    return this.traceId
  }

  currentEvents(): TraceEvent[] {
    return this.events
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  clearView(): void {
    this.events = []
    this.persist()
    this.notify()
  }

  append(event: TraceEvent, sync = true): void {
    const next: TraceEvent = {
      ...event,
      traceId: event.traceId || this.traceId,
      timestamp: event.timestamp || nowStamp(),
    }
    const key = `${next.timestamp}|${next.component}|${next.operation}|${next.message}`
    if (this.events.some((item) => `${item.timestamp}|${item.component}|${item.operation}|${item.message}` === key)) {
      return
    }
    this.events = [...this.events, next].slice(-MAX_EVENTS)
    this.persist()
    this.notify()
    if (sync && next.source !== 'backend') {
      this.pending.push(next)
      this.scheduleFlush()
    }
  }

  mergeBackend(events: TraceEvent[]): void {
    for (const event of events) {
      this.append({ ...event, source: 'backend' }, false)
    }
  }

  info(component: TraceEvent['component'], operation: string, message: string, metadata?: Record<string, unknown>): void {
    this.append({
      traceId: this.traceId,
      timestamp: nowStamp(),
      level: 'INFO',
      component,
      operation,
      message,
      metadata,
      source: 'ui',
    })
  }

  warn(component: TraceEvent['component'], operation: string, message: string, metadata?: Record<string, unknown>): void {
    this.append({
      traceId: this.traceId,
      timestamp: nowStamp(),
      level: 'WARN',
      component,
      operation,
      message,
      metadata,
      source: 'ui',
    })
  }

  error(
    component: TraceEvent['component'],
    operation: string,
    message: string,
    metadata?: Record<string, unknown>,
    error?: unknown,
  ): void {
    this.append({
      traceId: this.traceId,
      timestamp: nowStamp(),
      level: 'ERROR',
      component,
      operation,
      message,
      metadata,
      error: error instanceof Error ? error.message : String(error ?? message),
      exceptionType: error instanceof Error ? error.name : undefined,
      source: 'ui',
    })
  }

  private persist(): void {
    try {
      sessionStorage.setItem(TRACE_ID_KEY, this.traceId)
      sessionStorage.setItem(TRACE_EVENTS_KEY, JSON.stringify(this.events.slice(-MAX_EVENTS)))
    } catch {
      // storage full or unavailable
    }
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener()
    }
  }

  private scheduleFlush(): void {
    if (this.flushTimer != null) {
      return
    }
    this.flushTimer = window.setTimeout(() => {
      this.flushTimer = null
      void this.flush()
    }, 400)
  }

  private async flush(): Promise<void> {
    if (this.pending.length === 0) {
      return
    }
    const batch = this.pending.splice(0, this.pending.length)
    try {
      await fetch(`/api/debug/traces/${encodeURIComponent(this.traceId)}/events`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-SmartQA-Trace-Id': this.traceId,
        },
        body: JSON.stringify(batch),
      })
    } catch {
      this.pending.unshift(...batch)
    }
  }
}

export const traceStore = new TraceStore()

export const traceLogger = {
  start: (operation: string, metadata?: Record<string, unknown>) => traceStore.start(operation, metadata),
  info: (component: TraceEvent['component'], operation: string, message: string, metadata?: Record<string, unknown>) =>
    traceStore.info(component, operation, message, metadata),
  warn: (component: TraceEvent['component'], operation: string, message: string, metadata?: Record<string, unknown>) =>
    traceStore.warn(component, operation, message, metadata),
  error: (
    component: TraceEvent['component'],
    operation: string,
    message: string,
    metadata?: Record<string, unknown>,
    error?: unknown,
  ) => traceStore.error(component, operation, message, metadata, error),
  getTraceId: () => traceStore.getTraceId(),
}
