import { useEffect, useState } from 'react'
import { Timer } from 'lucide-react'
import { cn } from '../../lib/cn'
import { formatRemaining, parseServerTime } from '../../lib/format'

/**
 * 倒计时：每秒刷新一次，目标是服务端时间字段（start/end）。
 * 到点后自动回调 `onFinish`（由父组件刷新活动状态，而不是自行猜测结果）。
 */
export function Countdown({
  target,
  label,
  onFinish,
  className,
}: {
  target: string | undefined
  label: string
  onFinish?: () => void
  className?: string
}) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  const deadline = parseServerTime(target)
  const finished = deadline ? deadline.getTime() <= now : false

  useEffect(() => {
    if (finished) {
      onFinish?.()
    }
    // 只在"刚结束"这一刻触发一次
  }, [finished, onFinish])

  return (
    <div className={cn('flex items-center gap-1.5', className)}>
      <Timer className="size-4 text-brand" aria-hidden />
      <span className="text-xs text-ink-600">{label}</span>
      <span className="tnum text-sm font-semibold text-brand" role="timer" aria-live="off">
        {formatRemaining(target, now)}
      </span>
    </div>
  )
}
