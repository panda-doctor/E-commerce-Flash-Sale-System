import { cn } from '../../lib/cn'

type Tone = 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

/**
 * 徽标：浅底 + 深字 + 同色描边。
 * brand 用浅红底 + 深红字（小字号 5.9:1），不使用纯红实心大面积色块。
 */
const TONE: Record<Tone, string> = {
  brand: 'bg-brand-soft text-brand-dark border-brand/20',
  success: 'bg-state-success/10 text-state-success border-state-success/20',
  warning: 'bg-state-warning/10 text-state-warning border-state-warning/20',
  danger: 'bg-state-danger/10 text-state-danger border-state-danger/20',
  info: 'bg-page-subtle text-ink-600 border-state-line',
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
