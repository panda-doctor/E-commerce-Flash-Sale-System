import { cn } from '../../lib/cn'

type Tone = 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

/**
 * 徽标配色：浅底 + 深字 + 同色描边。
 * - brand 专用于促销标记（秒杀、限时），是全站唯一的红色徽标；
 * - 其余色调只对应真实业务状态，不用于装饰；
 * - info 用中性深灰，避免蓝色成为第二个强调色。
 */
const TONE: Record<Tone, string> = {
  brand: 'bg-brand-soft text-brand-ink border-brand/20',
  success: 'bg-state-success/10 text-state-success border-state-success/20',
  warning: 'bg-state-warning/10 text-state-warning border-state-warning/20',
  danger: 'bg-state-danger/10 text-state-danger border-state-danger/20',
  info: 'bg-page text-ink-600 border-state-line',
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
