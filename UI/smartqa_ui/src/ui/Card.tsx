import type { ReactNode } from 'react'

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <article className={`rounded-xl border border-border bg-panel p-5 shadow-sm ${className}`}>
      {children}
    </article>
  )
}
