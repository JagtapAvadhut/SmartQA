export function collapseRepeatedInstructions(text: string): string {
  const trimmed = text.replaceAll('\r\n', '\n').trim()
  if (!trimmed) {
    return ''
  }
  const lines = trimmed.split('\n')
  const firstIdx = lines.findIndex((line) => line.trim().length > 0)
  if (firstIdx < 0) {
    return ''
  }
  const first = lines[firstIdx].trim()
  if (first.length >= 8) {
    const secondIdx = lines.findIndex((line, index) => index > firstIdx && line.trim() === first)
    if (secondIdx > firstIdx) {
      const firstBlock = lines.slice(0, secondIdx).join('\n').trim()
      const secondBlock = lines.slice(secondIdx).join('\n').trim()
      if (firstBlock && firstBlock === secondBlock) {
        return firstBlock
      }
    }
  }
  return collapseHalves(trimmed)
}

function collapseHalves(text: string): string {
  const compact = text.trim()
  const mid = Math.floor(compact.length / 2)
  if (mid < 40) {
    return compact
  }
  for (const sep of ['\n\n', '\n', '']) {
    const split = compact.indexOf(sep, mid - Math.min(80, mid))
    if (split < 20) {
      continue
    }
    const left = compact.slice(0, split).trim()
    const right = compact.slice(split + sep.length).trim()
    if (left && left === right) {
      return left
    }
  }
  return compact
}
