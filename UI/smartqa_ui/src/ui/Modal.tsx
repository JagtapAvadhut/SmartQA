import type { ReactNode } from 'react'

export function Modal({
  title,
  children,
  onClose,
}: {
  title: string
  children: ReactNode
  onClose?: () => void
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-lg rounded-xl border border-border bg-panel p-5 shadow-xl">
        <div className="mb-4 flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold">{title}</h2>
          {onClose ? (
            <button className="text-muted hover:text-text" type="button" onClick={onClose}>
              Close
            </button>
          ) : null}
        </div>
        {children}
      </div>
    </div>
  )
}
