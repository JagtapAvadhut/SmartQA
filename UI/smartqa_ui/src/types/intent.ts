export interface ClarificationQuestion {
  id: string
  question: string
  options: string[]
}

export interface IntentFilter {
  field: string | null
  operator: string | null
  value: string | null
  min: number | null
  max: number | null
}

export interface IntentStep {
  id: string
  action: string
  target: string | null
  value: string | null
  assertion: string | null
  filter?: IntentFilter | null
  location?: string | null
  scenarioId?: string | null
  targetType?: string | null
  controlType?: string | null
  containerContext?: string | null
  dependsOn?: string[]
  preconditions?: string[]
  expectedState?: string | null
  postconditions?: string[]
  timeoutPolicy?: string | null
  recoveryPolicy?: string | null
  semanticConstraints?: string[]
}

export interface IntentScenario {
  id: string
  name: string
  steps: IntentStep[]
}

export interface IntentContract {
  status: string
  testName: string
  confidence: number | null
  scenarios: IntentScenario[]
  clarifications: ClarificationQuestion[]
}

export interface ProgressEvent {
  type: string
  message: string
  testCaseId: string | null
  executionRunId: string | null
  timestamp: string
  details: Record<string, unknown>
  generationRunId?: string | null
  pipelineRunId?: string | null
  stepNumber?: number | null
  totalSteps?: number | null
  currentUrl?: string | null
  pageTitle?: string | null
  executionProvider?: string | null
  screenshotId?: string | null
  eventId?: number | null
}

export interface ValidationRun {
  id: string
  testCaseId: string
  status: string
  result: string | null
  attemptNumber: number
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
  stdout: string | null
  stderr: string | null
  errorMessage: string | null
  createdAt: string
}

export interface ValidationResult {
  status: string
  steps: ValidationStepResult[]
  totalSteps: number
  passedSteps: number
  failedSteps: number
  failedStepNumber: number | null
  failedAction: string | null
  errorMessage: string | null
  screenshotId: string | null
  durationMs: number
  attemptNumber: number
}

export interface ValidationStepResult {
  stepNumber: number
  action: string | null
  target: string | null
  status: string
  errorMessage: string | null
  durationMs: number
}

export type ExecutionStepStatus = 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED' | 'STOPPED' | 'SKIPPED'

export interface ExecutionStep {
  stepNumber: number
  action: string
  target: string
  status: ExecutionStepStatus
  locator?: string | null
  controlType?: string | null
  confidence?: number | null
  durationMs?: number | null
  errorMessage?: string | null
}

export function parseValidationResult(raw: string | null): ValidationResult | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as ValidationResult
  } catch {
    return null
  }
}

export interface ExecutionRun {
  id: string
  testCaseId: string
  status: string
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
  exitCode: number | null
  stdout: string | null
  stderr: string | null
  errorMessage: string | null
  scenarioResults: string | null
  healingEvents: string | null
  createdAt: string
}

export interface AiSettings {
  provider: string
  primaryProvider: string
  fallbackProvider: string
  ollamaBaseUrl: string
  ollamaModel: string
  geminiModel: string
  geminiConfigured: boolean
  openaiCompatibleBaseUrl: string
  openaiCompatibleModel: string
  openaiCompatibleConfigured: boolean
  timeoutSeconds: number
}

export interface AiProviderHealth {
  provider: string
  model: string | null
  endpointHost: string | null
  status: string
  reason: string | null
  latencyMs: number
  configuredKeys?: number
  healthyKeys?: number
  cooldownKeys?: number
}

export interface AiHealthSnapshot {
  primaryProvider: string
  fallbackProvider: string | null
  providers: AiProviderHealth[]
}

export function parseIntent(raw: string | null): IntentContract | null {
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as IntentContract
  } catch {
    return null
  }
}

export interface LocatorMemoryEntry {
  stepId: string
  action: string
  semanticTarget: string | null
  resolvedLocator: string | null
  locatorType: string | null
  confidence: number
  elementText: string | null
  pageUrl: string | null
  healed: boolean
  locatorCloud: string | null
}

export function parseLocatorMemory(raw: string | null): LocatorMemoryEntry[] {
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as { entries?: LocatorMemoryEntry[] }
    return parsed.entries ?? []
  } catch {
    return []
  }
}

export function filterRows(intent: IntentContract | null): { field: string; value: string }[] {
  if (!intent?.scenarios) {
    return []
  }
  const rows: { field: string; value: string }[] = []
  for (const scenario of intent.scenarios) {
    for (const step of scenario.steps ?? []) {
      if (!step.filter?.field) {
        continue
      }
      const value =
        step.filter.operator === 'between' && step.filter.min != null && step.filter.max != null
          ? `${step.filter.min}–${step.filter.max}`
          : (step.filter.value ?? step.value ?? '')
      rows.push({ field: step.filter.field, value })
    }
  }
  return rows
}
