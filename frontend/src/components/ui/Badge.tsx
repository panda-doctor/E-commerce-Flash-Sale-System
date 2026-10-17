import { cn } from '../../lib/cn'

type Tone = 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const TONE: Record<Tone, string> = {
  brand: 'bg-brand-soft text-brand border-brand/20',
  success: 'bg-state-success/10 text-state-success border-state-success/20',
  warning: 'bg-state-warning/10 text-[#B26A00] border-state-warning/25',
  danger: 'bg-state-danger/10 text-state-danger border-state-danger/20',
  info: 'bg-state-info/10 text-state-info border-state-info/20',
  neutral: 'bg-page text-ink-600 border-state-line',
}

export function Badge({
  tone = 'neutral',
  children,
  className,
}: {
  tone?: Tone
  children: React.ReactNode
  className?: string
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium',
        TONE[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}
