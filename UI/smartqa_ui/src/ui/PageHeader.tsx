import type { ReactNode } from 'react'

export function PageHeader({
  kicker,
  title,
  description,
  actions,
}: {
  kicker?: string
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <header className="mb-7 flex flex-wrap items-end justify-between gap-4">
      <div>
        {kicker ? (
          <p className="mb-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-accent">{kicker}</p>
        ) : null}
        <h1 className="text-[28px] font-semibold tracking-tight">{title}</h1>
        {description ? <p className="mt-1 max-w-2xl text-sm text-muted">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </header>
  )
}
