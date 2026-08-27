export const MASK = '***MASKED***'

const SECRET_KEY = /(password|passwd|pwd|secret|token|api[_-]?key|authorization|cookie|session|credential|private[_-]?key)/i
const INLINE_SECRET = /(password|passwd|pwd|secret|token|api[_-]?key|authorization|cookie|session)=([^\s&"']+)/gi
const BEARER = /(bearer\s+)[A-Za-z0-9._\-=+/]+/gi

export function looksSecret(key: string | null | undefined, value: unknown): boolean {
  if (key && SECRET_KEY.test(key)) {
    return true
  }
  if (typeof value === 'string' && INLINE_SECRET.test(value)) {
    INLINE_SECRET.lastIndex = 0
    return true
  }
  INLINE_SECRET.lastIndex = 0
  return false
}

export function maskText(value: string | null | undefined): string {
  if (value == null) {
    return ''
  }
  return value.replace(INLINE_SECRET, `$1=${MASK}`).replace(BEARER, `$1${MASK}`)
}

export function maskValue(key: string | null | undefined, value: unknown): unknown {
  if (looksSecret(key, value)) {
    return MASK
  }
  if (typeof value === 'string') {
    return maskText(value)
  }
  return mask(value)
}

export function mask(value: unknown, depth = 0): unknown {
  if (value == null || depth > 8) {
    return value
  }
  if (Array.isArray(value)) {
    return value.map((item) => mask(item, depth + 1))
  }
  if (typeof value === 'object') {
    const out: Record<string, unknown> = {}
    for (const [key, nested] of Object.entries(value as Record<string, unknown>)) {
      out[key] = looksSecret(key, nested) ? MASK : mask(nested, depth + 1)
    }
    return out
  }
  if (typeof value === 'string') {
    return maskText(value)
  }
  return value
}

export function summarizePayload(value: unknown, limit = 1500): unknown {
  const masked = mask(value)
  const text = typeof masked === 'string' ? masked : JSON.stringify(masked, null, 2)
  if (!text) {
    return masked
  }
  if (text.length <= limit) {
    return masked
  }
  return {
    payloadSize: text.length,
    preview: `${text.slice(0, limit)}...[truncated]`,
  }
}
