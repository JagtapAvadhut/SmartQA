export type WorkspacePhase =
  | 'idle'
  | 'creating'
  | 'analyzing'
  | 'analysis-complete'
  | 'generating'
  | 'generated'
  | 'executing'
  | 'pipeline-running'
  | 'stopping'
  | 'completed'
  | 'failed'
  | 'stopped'
  | 'blocked'
  | 'abandoned'

const PROJECT_KEY = 'smartqa.workspace.projectId'
const TEST_KEY = 'smartqa.workspace.testCaseId'
const PIPELINE_KEY = 'smartqa.workspace.pipelineRunId'

export function readWorkspaceSession(): {
  projectId: string | null
  testCaseId: string | null
  pipelineRunId: string | null
} {
  return {
    projectId: window.localStorage.getItem(PROJECT_KEY),
    testCaseId: window.localStorage.getItem(TEST_KEY),
    pipelineRunId: window.localStorage.getItem(PIPELINE_KEY),
  }
}

export function writeWorkspaceSession(projectId: string, testCaseId: string, pipelineRunId?: string | null) {
  window.localStorage.setItem(PROJECT_KEY, projectId)
  window.localStorage.setItem(TEST_KEY, testCaseId)
  if (pipelineRunId) {
    window.localStorage.setItem(PIPELINE_KEY, pipelineRunId)
  } else if (pipelineRunId === null) {
    window.localStorage.removeItem(PIPELINE_KEY)
  }
}

export function clearWorkspaceSession() {
  window.localStorage.removeItem(PROJECT_KEY)
  window.localStorage.removeItem(TEST_KEY)
  window.localStorage.removeItem(PIPELINE_KEY)
}

export function formatLocator(type: string | null, locator: string | null): string {
  if (!locator) {
    return '—'
  }
  const kind = type ?? 'css'
  if (kind === 'role' && locator.includes('|')) {
    const [role, name] = locator.split('|', 2)
    return `getByRole("${role}", "${name}")`
  }
  if (kind === 'text') {
    return `getByText("${locator}")`
  }
  if (kind === 'label') {
    return `getByLabel("${locator}")`
  }
  if (kind === 'placeholder') {
    return `getByPlaceholder("${locator}")`
  }
  if (kind === 'css') {
    return `locator("${locator}")`
  }
  return locator
}
