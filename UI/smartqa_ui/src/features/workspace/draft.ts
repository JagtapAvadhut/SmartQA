const DRAFT_KEY = 'smartqa.workspace.draft'

export interface WorkspaceDraft {
  applicationUrl: string
  instructions: string
  instructionMode: 'structured' | 'paragraph'
  updatedAt: string
}

export function readWorkspaceDraft(): WorkspaceDraft | null {
  try {
    const raw = window.localStorage.getItem(DRAFT_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as WorkspaceDraft
    if (!parsed || typeof parsed.applicationUrl !== 'string') return null
    return parsed
  } catch {
    return null
  }
}

export function writeWorkspaceDraft(draft: Omit<WorkspaceDraft, 'updatedAt'>) {
  const payload: WorkspaceDraft = {
    ...draft,
    updatedAt: new Date().toISOString(),
  }
  window.localStorage.setItem(DRAFT_KEY, JSON.stringify(payload))
}

export function clearWorkspaceDraft() {
  window.localStorage.removeItem(DRAFT_KEY)
}
