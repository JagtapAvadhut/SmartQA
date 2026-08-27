import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'

const styles: Record<Variant, string> = {
  primary:
    'bg-accent text-bg hover:bg-accent/90 border-transparent',
  secondary:
    'bg-panel text-text border-border hover:border-accent/60',
  ghost:
    'bg-transparent text-muted border-transparent hover:text-text',
  danger:
    'bg-danger/15 text-danger border-danger/40 hover:bg-danger/25',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  children: ReactNode
}

export function Button({ variant = 'secondary', className = '', children, ...props }: ButtonProps) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg border px-3.5 py-2 text-sm font-medium disabled:opacity-50 ${styles[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
