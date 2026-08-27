import type { FailureDiagnosis, PipelineRun } from '../../api/pipeline'

/** Plain-language failure copy for normal users. Technical codes stay under View Details. */
export function humanFailureWhy(diagnosis: FailureDiagnosis | null | undefined, fallback?: string | null): string {
  if (!diagnosis) {
    return fallback?.trim() || 'SmartQA could not complete this test.'
  }
  const why = (diagnosis.whyFailed || '').trim()
  if (why && !looksTechnical(why)) {
    return why
  }
  const category = (diagnosis.category || '').toUpperCase()
  if (category.includes('FILTER')) {
    return 'SmartQA could not complete the filter selection.'
  }
  if (category.includes('SEARCH')) {
    return 'SmartQA could not complete the search as expected.'
  }
  if (category.includes('HOST') || category.includes('WRONG_PAGE')) {
    return 'The browser opened a different page than the one expected for this test.'
  }
  if (category.includes('ASSERT')) {
    return 'The expected result was not found on the page.'
  }
  if (category.includes('LOCATOR') || category.includes('ELEMENT') || category.includes('DOM')) {
    return 'SmartQA found the page, but could not locate the requested control.'
  }
  if (category.includes('WAIT')) {
    return 'The page did not reach the expected state in time.'
  }
  if (category.includes('AMBIGUOUS')) {
    return 'Several matching controls were found. SmartQA needs a clearer choice.'
  }
  return why || diagnosis.whatFailed || fallback?.trim() || 'SmartQA could not complete this test.'
}

export function humanAiDiagnosis(diagnosis: FailureDiagnosis | null | undefined): string | null {
  const ai = diagnosis?.aiDiagnosis
  if (!ai) return null
  const text = (ai.explanation || ai.rootCause || '').trim()
  if (!text) return null
  if (looksTechnical(text) && ai.classification) {
    return plainClassification(ai.classification)
  }
  return text
}

export function humanFailedStep(diagnosis: FailureDiagnosis | null | undefined): string {
  const evidence = diagnosis?.failureEvidence
  const parts = [evidence?.action, evidence?.target].filter(Boolean)
  if (parts.length) return parts.join(' ')
  return diagnosis?.whatFailed || 'Unknown step'
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(ms)) return '—'
  if (ms < 1000) return `${ms} ms`
  const sec = Math.round(ms / 1000)
  if (sec < 60) return `${sec}s`
  const min = Math.floor(sec / 60)
  const rem = sec % 60
  return `${min}m ${rem}s`
}

export function applicationLabel(url: string | null | undefined): string {
  if (!url) return 'Application'
  try {
    const host = new URL(url).hostname.replace(/^www\./, '')
    const first = host.split('.')[0]
    if (!first) return host
    return first.charAt(0).toUpperCase() + first.slice(1)
  } catch {
    return url
  }
}

export function pipelineStatusLabel(status: PipelineRun['status'] | string | null | undefined): string {
  switch (status) {
    case 'PASS':
      return 'Passed'
    case 'VALIDATED_NOT_EXECUTED':
      return 'Validated (not executed)'
    case 'FAIL':
      return 'Failed'
    case 'BLOCKED':
      return 'Needs your input'
    case 'STOPPED':
      return 'Stopped'
    case 'ABANDONED':
      return 'Abandoned after restart'
    case 'RUNNING':
    case 'QUEUED':
      return 'Running'
    default:
      return status || 'Unknown'
  }
}

function looksTechnical(text: string): boolean {
  const lower = text.toLowerCase()
  return (
    /[A-Z]{3,}_[A-Z0-9_]+/.test(text) ||
    lower.includes('exception') ||
    lower.includes('nullpointer') ||
    lower.includes('stacktrace') ||
    lower.includes('sse') ||
    lower.includes('intent_contract')
  )
}

function plainClassification(classification: string): string {
  const c = classification.toUpperCase()
  if (c.includes('FILTER')) return 'The filter option was not found or did not update the results.'
  if (c.includes('HOST')) return 'The browser landed on an unexpected site or host.'
  if (c.includes('ASSERT')) return 'The expected text or result was missing.'
  if (c.includes('AMBIGUOUS')) return 'More than one control matched the instruction.'
  if (c.includes('SEARCH')) return 'Search did not reach the expected results.'
  return classification.replaceAll('_', ' ').toLowerCase()
}
