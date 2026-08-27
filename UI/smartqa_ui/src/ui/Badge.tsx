type Tone = 'ok' | 'danger' | 'accent' | 'muted'

const tones: Record<Tone, string> = {
  ok: 'bg-ok/15 text-ok',
  danger: 'bg-danger/15 text-danger',
  accent: 'bg-accent/15 text-accent',
  muted: 'bg-white/5 text-muted',
}

export function Badge({ tone = 'muted', children }: { tone?: Tone; children: string }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}
