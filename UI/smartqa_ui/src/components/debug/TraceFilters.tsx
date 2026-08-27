import type { TraceComponent, TraceLevel } from '../../services/traceLogger'

const LEVELS: Array<TraceLevel | 'ALL'> = ['ALL', 'INFO', 'DEBUG', 'WARN', 'ERROR']
const COMPONENTS: Array<TraceComponent | 'ALL'> = [
  'ALL',
  'UI',
  'HTTP',
  'CONTROLLER',
  'SERVICE',
  'AI',
  'BROWSER',
  'DOM',
  'LOCATOR',
  'PLAYWRIGHT',
  'SSE',
  'CODEGEN',
  'QUALITY_GATE',
  'EXECUTION',
]

interface TraceFiltersProps {
  level: TraceLevel | 'ALL'
  component: TraceComponent | 'ALL'
  onLevel: (level: TraceLevel | 'ALL') => void
  onComponent: (component: TraceComponent | 'ALL') => void
}

export function TraceFilters({ level, component, onLevel, onComponent }: TraceFiltersProps) {
  return (
    <div className="trace-filters">
      <label>
        Level
        <select value={level} onChange={(event) => onLevel(event.target.value as TraceLevel | 'ALL')}>
          {LEVELS.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
      </label>
      <label>
        Component
        <select value={component} onChange={(event) => onComponent(event.target.value as TraceComponent | 'ALL')}>
          {COMPONENTS.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
