import { useEffect, useMemo, useRef, useState } from 'react'
import { downloadTraceUrl, getTrace } from '../../api/debug'
import { formatTrace } from '../../services/formatTrace'
import type { TraceComponent, TraceEvent, TraceLevel } from '../../services/traceLogger'
import { traceStore } from '../../services/traceLogger'
import { TraceEventRow } from './TraceEventRow'
import { TraceFilters } from './TraceFilters'
import { TraceSummary, summarizeEvents } from './TraceSummary'

interface DebugTracePanelProps {
  running?: boolean
}

export function DebugTracePanel({ running = false }: DebugTracePanelProps) {
  const [events, setEvents] = useState<TraceEvent[]>(traceStore.currentEvents())
  const [traceId, setTraceId] = useState(traceStore.getTraceId())
  const [level, setLevel] = useState<TraceLevel | 'ALL'>('ALL')
  const [component, setComponent] = useState<TraceComponent | 'ALL'>('ALL')
  const [autoScroll, setAutoScroll] = useState(true)
  const [copied, setCopied] = useState(false)
  const scroller = useRef<HTMLDivElement>(null)
  const stickToBottom = useRef(true)

  useEffect(() => {
    return traceStore.subscribe(() => {
      setEvents([...traceStore.currentEvents()])
      setTraceId(traceStore.getTraceId())
    })
  }, [])

  useEffect(() => {
    if (!running && events.length === 0) {
      return
    }
    let cancelled = false
    const tick = () => {
      getTrace(traceStore.getTraceId())
        .then((response) => {
          if (!cancelled && response.data?.events) {
            traceStore.mergeBackend(response.data.events)
          }
        })
        .catch(() => undefined)
    }
    tick()
    const handle = window.setInterval(tick, 1000)
    return () => {
      cancelled = true
      window.clearInterval(handle)
    }
  }, [running, traceId])

  const filtered = useMemo(() => {
    return events.filter((event) => {
      if (level !== 'ALL' && event.level !== level) {
        return false
      }
      if (component !== 'ALL' && String(event.component).toUpperCase() !== component) {
        return false
      }
      return true
    })
  }, [events, level, component])

  const summary = summarizeEvents(events)

  useEffect(() => {
    if (!autoScroll || !stickToBottom.current || !scroller.current) {
      return
    }
    scroller.current.scrollTop = scroller.current.scrollHeight
  }, [filtered, autoScroll])

  function onScroll() {
    const node = scroller.current
    if (!node) {
      return
    }
    const atBottom = node.scrollHeight - node.scrollTop - node.clientHeight < 48
    stickToBottom.current = atBottom
    if (!atBottom && autoScroll) {
      setAutoScroll(false)
    }
  }

  async function onCopy() {
    const text = formatTrace(traceId, events)
    await navigator.clipboard.writeText(text)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  function onDownload(format: 'log' | 'jsonl') {
    const href = downloadTraceUrl(traceId, format)
    const link = document.createElement('a')
    link.href = href
    link.download = `smartqa-trace-${traceId}.${format === 'jsonl' ? 'jsonl' : 'log'}`
    document.body.appendChild(link)
    link.click()
    link.remove()
  }

  async function onDownloadFallback() {
    try {
      onDownload('log')
    } catch {
      const blob = new Blob([formatTrace(traceId, events)], { type: 'text/plain' })
      const href = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = href
      link.download = `smartqa-trace-${traceId}.log`
      link.click()
      URL.revokeObjectURL(href)
    }
  }

  return (
    <article className="card debug-trace-panel">
      <h2>Debug Trace</h2>
      <TraceSummary traceId={traceId} {...summary} />
      <div className="toolbar">
        <button className="btn" type="button" onClick={() => void onCopy()}>
          {copied ? 'Copied' : 'Copy Trace'}
        </button>
        <button className="btn" type="button" onClick={() => void onDownloadFallback()}>
          Download Trace
        </button>
        <button className="btn" type="button" onClick={() => onDownload('jsonl')}>
          Download JSONL
        </button>
        <button className="btn" type="button" onClick={() => traceStore.clearView()}>
          Clear
        </button>
        <button
          className={`btn ${autoScroll ? 'primary' : ''}`}
          type="button"
          onClick={() => {
            setAutoScroll((value) => !value)
            stickToBottom.current = true
          }}
        >
          Auto-scroll {autoScroll ? 'ON' : 'OFF'}
        </button>
      </div>
      <TraceFilters level={level} component={component} onLevel={setLevel} onComponent={setComponent} />
      <h3>Live Trace</h3>
      <div className="trace-live" ref={scroller} onScroll={onScroll}>
        {filtered.length === 0 ? <p className="muted">No trace events yet.</p> : null}
        {filtered.map((event, index) => (
          <TraceEventRow key={`${event.timestamp}-${event.operation}-${index}`} event={event} />
        ))}
      </div>
    </article>
  )
}
