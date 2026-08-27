import type { TraceEvent } from './traceLogger'

export function formatTrace(traceId: string, events: TraceEvent[]): string {
  const started = events[0]?.timestamp ?? ''
  const finished = events.at(-1)?.timestamp ?? ''
  const errors = events.filter((event) => event.level === 'ERROR')
  const warnings = events.filter((event) => event.level === 'WARN')
  const operation = firstMeta(events, 'operation') || firstOperation(events, 'TRACE_STARTED')
  const url = firstMeta(events, 'applicationUrl') || firstMeta(events, 'url')
  const duration = totalDuration(events)
  const status = errors.length > 0 ? 'FAILED' : lastOperation(events, 'TRACE_END') ? 'COMPLETED' : 'RUNNING'

  const lines: string[] = [
    'SMARTQA TRACE',
    '================================================',
    '',
    'Trace ID:',
    traceId,
    '',
    'Started:',
    started,
    '',
    'Finished:',
    finished,
    '',
    'Operation:',
    String(operation ?? ''),
    '',
    'Application URL:',
    String(url ?? ''),
    '',
    `STATUS: ${status}`,
    `EVENTS: ${events.length}`,
    `ERRORS: ${errors.length}`,
    `WARNINGS: ${warnings.length}`,
    `DURATION: ${duration}`,
    '',
  ]

  let step = 1
  let lastComponent = ''
  for (const event of events) {
    const component = event.component || 'UNKNOWN'
    if (component !== lastComponent) {
      lines.push('------------------------------------------------')
      lines.push(`STEP ${step} — ${component}`)
      lines.push('------------------------------------------------')
      lines.push('')
      step += 1
      lastComponent = component
    }
    appendEvent(lines, event)
  }

  if (errors.length > 0) {
    lines.push('------------------------------------------------')
    lines.push('ERROR')
    lines.push('------------------------------------------------')
    lines.push('')
    for (const event of errors) {
      appendEvent(lines, event)
    }
  }

  lines.push('================================================')
  lines.push('')
  lines.push('TRACE END')
  return lines.join('\n')
}

function appendEvent(lines: string[], event: TraceEvent): void {
  lines.push('EVENT:')
  lines.push(event.operation)
  lines.push('')
  lines.push(`timestamp: ${event.timestamp}`)
  lines.push(`level: ${event.level}`)
  lines.push(`component: ${event.component}`)
  lines.push(`message: ${event.message}`)
  if (event.durationMs != null) {
    lines.push(`durationMs: ${event.durationMs}`)
  }
  if (event.exceptionType) {
    lines.push(`exceptionType: ${event.exceptionType}`)
  }
  if (event.error) {
    lines.push(`error: ${event.error}`)
  }
  if (event.payload != null) {
    lines.push('payload:')
    lines.push(stringify(event.payload))
  }
  if (event.result != null) {
    lines.push('result:')
    lines.push(stringify(event.result))
  }
  if (event.metadata && Object.keys(event.metadata).length > 0) {
    lines.push('metadata:')
    for (const [key, value] of Object.entries(event.metadata)) {
      lines.push(`  ${key}=${stringify(value)}`)
    }
  }
  if (event.stackTrace) {
    lines.push('stackTrace:')
    lines.push(event.stackTrace)
  }
  lines.push('')
}

function firstMeta(events: TraceEvent[], key: string): unknown {
  for (const event of events) {
    if (event.metadata && event.metadata[key] != null) {
      return event.metadata[key]
    }
  }
  return ''
}

function firstOperation(events: TraceEvent[], operation: string): string {
  return events.find((event) => event.operation === operation)?.message ?? ''
}

function lastOperation(events: TraceEvent[], operation: string): TraceEvent | undefined {
  return [...events].reverse().find((event) => event.operation === operation)
}

function totalDuration(events: TraceEvent[]): string {
  const total = events.reduce((sum, event) => sum + (event.durationMs ?? 0), 0)
  return total > 0 ? `${(total / 1000).toFixed(2)}s` : ''
}

function stringify(value: unknown): string {
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
