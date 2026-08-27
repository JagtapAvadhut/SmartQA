import { ApiError } from '../../api/client'

export function friendlyError(error: unknown): { message: string; details: string | null } {
  if (error instanceof ApiError) {
    const details = [
      error.errorCode ?? 'ERROR',
      `status=${error.status}`,
      error.url ? `url=${error.url}` : null,
      error.causeType ? `errorType=${error.causeType}` : null,
      `durationMs=${error.durationMs}`,
    ]
      .filter(Boolean)
      .join(' ')
    return { message: mapApiMessage(error), details }
  }
  if (error instanceof TypeError) {
    return {
      message: 'SmartQA backend is not connected. Start the backend and try again.',
      details: error.message,
    }
  }
  if (error instanceof Error) {
    return { message: mapRawMessage(error.message), details: error.message }
  }
  return { message: 'Something went wrong. Try again.', details: String(error) }
}

function mapApiMessage(error: ApiError): string {
  const raw = error.message || ''
  const lower = raw.toLowerCase()
  const code = error.errorCode ?? ''
  if (code === 'AMBIGUOUS_ELEMENT') {
    return 'Several matching controls were found. Clarify which one you meant.'
  }
  if (code === 'ELEMENT_NOT_FOUND' || code === 'LOCATOR_NOT_FOUND') {
    return 'SmartQA found the page, but could not locate the requested control.'
  }
  if (code === 'FILTER_VALIDATION_FAILURE' || code === 'FILTER_APPLICATION_FAILURE') {
    return 'The filter was found, but the result did not update.'
  }
  if (code === 'WAIT_TIMEOUT' || code === 'EXECUTION_TIMEOUT') {
    return 'The page did not reach the expected state in time.'
  }
  if (code === 'NETWORK_FAILURE' || code === 'APPLICATION_ERROR') {
    return 'The application or network returned an error during the test.'
  }
  if (code === 'INTENT_INVALID' || code === 'INTENT_PARSE_FAILED') {
    return 'SmartQA could not understand the test instructions.'
  }
  if (error.errorCode === 'AI_PROVIDERS_UNAVAILABLE') {
    return 'AI is not available right now. Check Settings and try again.'
  }
  if (error.errorCode === 'AI_TIMEOUT' || lower.includes('did not respond within')) {
    return 'AI took too long to respond. Try again in a moment.'
  }
  if (error.status === 0) {
    return 'SmartQA backend is not connected.'
  }
  return mapRawMessage(raw)
}

function mapRawMessage(raw: string): string {
  const lower = raw.toLowerCase()
  if (lower.includes('failed to fetch') || lower.includes('networkerror') || lower.includes('load failed')) {
    return 'SmartQA backend is not connected.'
  }
  if (lower.includes('nullpointer') || lower.includes('exception')) {
    return 'Something went wrong while running the test. Open technical details if you need more information.'
  }
  if (lower.includes('locator') || lower.includes('unable to resolve') || lower.includes('element')) {
    return 'SmartQA found the page, but could not locate the requested control.'
  }
  if (lower.includes('browser') || lower.includes('playwright')) {
    return 'SmartQA could not open the browser.'
  }
  if (lower.includes('ai') || lower.includes('ollama') || lower.includes('gemini') || lower.includes('timeout')) {
    return 'AI analysis failed. Check Settings and try again.'
  }
  if (raw.trim() && !/[A-Z]{3,}_[A-Z0-9_]+/.test(raw)) {
    return raw
  }
  if (raw.trim()) {
    return 'Something went wrong. Try again.'
  }
  return 'Something went wrong. Try again.'
}
