export type ProgressStepStatus = 'pending' | 'running' | 'completed' | 'failed'

export const PIPELINE_USER_STEPS = [
  { key: 'understand', label: 'Understanding test' },
  { key: 'open', label: 'Opening website' },
  { key: 'discover', label: 'Finding elements' },
  { key: 'execute', label: 'Running actions' },
  { key: 'verify', label: 'Verifying results' },
  { key: 'generate', label: 'Generating test' },
  { key: 'validate', label: 'Validating test' },
  { key: 'report', label: 'Final result' },
] as const

export function computePipelineProgress(
  userProgress: string[],
  currentLabel: string | null | undefined,
  status: string | null | undefined,
): { key: string; label: string; status: ProgressStepStatus }[] {
  const seen = new Set(userProgress.map((s) => s.toLowerCase()))
  const current = (currentLabel ?? '').toLowerCase()
  const failed = status === 'FAIL' || status === 'FAILED'
  const blocked = status === 'BLOCKED'
  const stopped = status === 'STOPPED'
  const passed = status === 'PASS' || status === 'PASSED'

  return PIPELINE_USER_STEPS.map((step) => {
    const labelLower = step.label.toLowerCase()
    const isCurrent = current.length > 0 && (current.includes(labelLower) || labelLower.includes(current))
    if (isCurrent) {
      if (failed || blocked || stopped) {
        return { key: step.key, label: step.label, status: 'failed' as ProgressStepStatus }
      }
      return { key: step.key, label: step.label, status: 'running' as ProgressStepStatus }
    }
    if (passed || seen.has(labelLower)) {
      return { key: step.key, label: step.label, status: 'completed' as ProgressStepStatus }
    }
    return { key: step.key, label: step.label, status: 'pending' as ProgressStepStatus }
  })
}

const LABELS: Record<string, string> = {
  GENERATION_STARTED: 'Starting test generation',
  INTENT_ANALYZED: 'Test understanding ready',
  INTENT_READY: 'Intent ready',
  AI_PROVIDER_SELECTED: 'AI provider selected',
  AI_FALLBACK_STARTED: 'Switching AI provider',
  AI_FALLBACK_SUCCEEDED: 'Fallback AI provider succeeded',
  AI_WAIT: 'Waiting for AI',
  AI_PREFLIGHT_SKIPPED: 'AI health check skipped',
  OLLAMA_FALLBACK_STARTED: 'Using local AI fallback',
  CLARIFICATION_REQUIRED: 'Clarification needed',
  BROWSER_STARTED: 'Opening browser',
  PAGE_LOADED: 'Page loaded',
  DOM_FETCHED: 'Inspecting page elements',
  ELEMENT_INVENTORY_BUILT: 'Element inventory built',
  ELEMENT_TREE_BUILT: 'Element tree built',
  ELEMENT_GRAPH_BUILT: 'Relationship graph built',
  TREE_GRAPH_RECONCILED: 'Tree and graph reconciled',
  CONTEXT_DETECTED: 'Context detected',
  SCOPE_ESTABLISHED: 'Search scope established',
  ELEMENT_CANDIDATES_FOUND: 'Candidates discovered',
  CONTROL_CLASSIFIED: 'Control type identified',
  ELEMENT_DISCOVERED: 'Element found',
  ELEMENT_HIGHLIGHTED: 'Highlighting target element',
  LOCATOR_SELECTED: 'Selecting the best locator',
  LOCATOR_RESOLVED: 'Locator verified',
  ACTION_COMPATIBILITY_VERIFIED: 'Action compatibility verified',
  ASSOCIATED_CONTROL_FOUND: 'Associated control found',
  ACTION_ELEMENT_MISMATCH: 'Action/element mismatch detected',
  LABEL_CONTROL_REDISCOVERY: 'Searching for associated control',
  FILTER_APPLIED: 'Filter applied',
  STATE_CHANGED: 'Page updated',
  STEP_STARTED: 'Running step',
  STEP_COMPLETED: 'Step completed',
  CODE_GENERATING: 'Creating Playwright test',
  CODE_GENERATED: 'Playwright test created',
  QUALITY_GATE_STARTED: 'Running quality checks',
  QUALITY_GATE_PASSED: 'Test quality check passed',
  QUALITY_GATE_FAILED: 'Quality check failed',
  GENERATION_COMPLETE: 'Test generation completed',
  GENERATION_ERROR: 'Test generation failed',
  EXECUTION_STARTED: 'Starting test',
  SCENARIO_STARTED: 'Scenario started',
  STEP_PASSED: 'Step passed',
  STEP_FAILED: 'Step failed',
  HEALING_STARTED: 'Trying a better locator',
  HEALING_SUCCESS: 'Locator healed',
  HEALING_COMPLETED: 'Healing complete',
  SCREENSHOT_CAPTURED: 'Screenshot captured',
  EXECUTION_COMPLETED: 'Test execution completed',
  EXECUTION_FAILED: 'Test execution failed',
  EXECUTION_STOP_REQUESTED: 'Stopping test...',
  EXECUTION_STOPPED: 'Test stopped',
  ACTION_STARTED: 'Performing action',
  ACTION_COMPLETED: 'Action completed',
  ACTION_FAILED: 'Action failed',
  LOCATOR_VERIFIED: 'Locator verified',
  AMBIGUOUS_ELEMENT: 'Ambiguous element',
  ELEMENT_NOT_FOUND: 'Element not found',
  ASSERTION_VERIFIED: 'Assertion verified',
  ASSERTION_FAILED: 'Assertion failed',
  REQUEST_FAILED: 'Request failed',
  CONSOLE_ERROR: 'Console error',
  VALIDATION_STARTED: 'Validation started',
  VALIDATION_STEP_STARTED: 'Validation step started',
  VALIDATION_STEP_PASSED: 'Validation step passed',
  VALIDATION_STEP_FAILED: 'Validation step failed',
  VALIDATION_COMPLETED: 'Validation completed',
  MCP_REQUEST_STARTED: 'MCP request started',
  MCP_REQUEST_COMPLETED: 'MCP request completed',
  CDP_EVENT: 'CDP event',
  PAGE_NAVIGATION_STARTED: 'Navigation started',
  DOM_FETCH_STARTED: 'Fetching page structure',
}

export function eventLabel(type: string, fallback?: string): string {
  return LABELS[type] ?? fallback ?? type.replaceAll('_', ' ').toLowerCase()
}

export const ANALYZE_STEPS = [
  { type: 'GENERATION_STARTED', label: 'Instructions received' },
  { type: 'INTENT_ANALYZED', label: 'Intent extracted' },
  { type: 'STEPS', label: 'Steps identified' },
  { type: 'ASSERTIONS', label: 'Assertions identified' },
]

export const GENERATE_STEPS = [
  { type: 'BROWSER_STARTED', label: 'Browser started' },
  { type: 'PAGE_LOADED', label: 'Page loaded' },
  { type: 'DOM_FETCHED', label: 'Page structure analyzed' },
  { type: 'ELEMENT_DISCOVERED', label: 'Element discovered' },
  { type: 'LOCATOR_SELECTED', label: 'Locator selected' },
  { type: 'CODE_GENERATING', label: 'Writing Playwright test' },
  { type: 'QUALITY_GATE_PASSED', label: 'Quality gate passed' },
  { type: 'GENERATION_COMPLETE', label: 'Ready to execute' },
]

export const GENERATION_PROGRESS_STEPS = [
  { key: 'analyze', label: 'Analyze instructions', events: ['GENERATION_STARTED', 'INTENT_ANALYZED'] },
  { key: 'browser', label: 'Open browser', events: ['BROWSER_STARTED'] },
  { key: 'discover', label: 'Discover elements', events: ['PAGE_LOADED', 'DOM_FETCHED', 'ELEMENT_DISCOVERED'] },
  { key: 'locators', label: 'Resolve locators', events: ['LOCATOR_SELECTED', 'LOCATOR_RESOLVED'] },
  { key: 'codegen', label: 'Generate Playwright code', events: ['CODE_GENERATING', 'CODE_GENERATED'] },
  { key: 'quality', label: 'Run quality checks', events: ['QUALITY_GATE_STARTED', 'QUALITY_GATE_PASSED'] },
  { key: 'ready', label: 'Ready', events: ['GENERATION_COMPLETE'] },
] as const

export function computeGenerationProgress(
  eventTypes: Set<string>,
  failed: boolean,
): { key: string; label: string; status: ProgressStepStatus }[] {
  const steps = GENERATION_PROGRESS_STEPS.map((step) => ({ ...step, status: 'pending' as ProgressStepStatus }))
  let lastCompleted = -1

  for (let i = 0; i < steps.length; i++) {
    const hasAny = steps[i].events.some((e) => eventTypes.has(e))
    if (hasAny) {
      steps[i].status = 'completed'
      lastCompleted = i
    }
  }

  if (failed) {
    const failIdx = lastCompleted >= 0 ? lastCompleted : 0
    steps[failIdx].status = 'failed'
  } else if (lastCompleted >= 0 && lastCompleted < steps.length - 1) {
    steps[lastCompleted + 1].status = 'running'
  }

  return steps
}
