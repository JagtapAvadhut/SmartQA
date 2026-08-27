export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="rounded-xl border border-dashed border-border px-6 py-10 text-center">
      <p className="font-medium text-text">{title}</p>
      {detail ? <p className="mt-1 text-sm text-muted">{detail}</p> : null}
    </div>
  )
}
