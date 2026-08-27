export function StatusDot({ ok, checking = false }: { ok: boolean; checking?: boolean }) {
  const color = checking ? 'bg-muted' : ok ? 'bg-ok' : 'bg-danger'
  return (
    <span className="relative inline-flex h-2.5 w-2.5">
      {checking || ok ? (
        <span className={`absolute inline-flex h-full w-full rounded-full opacity-60 ${color} ${checking ? 'animate-ping' : ''}`} />
      ) : null}
      <span className={`relative inline-flex h-2.5 w-2.5 rounded-full ${color}`} />
    </span>
  )
}
