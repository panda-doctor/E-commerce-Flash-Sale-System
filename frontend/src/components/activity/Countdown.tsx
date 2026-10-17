import { useEffect, useRef, useState } from 'react'
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

  // 回调放进 ref：父组件普遍传内联箭头函数，每次渲染都是新引用，
  // 直接放进依赖会让 effect 在归零后**每次渲染都执行**（refetch → 重渲染 → 再 refetch）。
  const onFinishRef = useRef(onFinish)
  useEffect(() => {
    onFinishRef.current = onFinish
  }, [onFinish])

  // 归零只回调一次；切换到另一个目标（target 变化）时重新武装
  const firedRef = useRef(false)
  useEffect(() => {
    firedRef.current = false
  }, [target])

  useEffect(() => {
    if (!finished || firedRef.current) {
      return
    }
    firedRef.current = true
    onFinishRef.current?.()
  }, [finished])

  return (
    <div className={cn('flex items-center gap-1.5', className)}>
      <Timer className="size-4 text-brand-dark" aria-hidden />
      <span className="text-xs text-ink-600">{label}</span>
      <span className="tnum text-sm font-bold text-brand-dark" role="timer" aria-live="off">
        {formatRemaining(target, now)}
      </span>
    </div>
  )
}
