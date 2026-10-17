import { AlertTriangle, RotateCcw } from 'lucide-react'
import { Button } from './Button'
import { requestIdOf, toMessage } from '../../lib/errors'

export function ErrorState({
  error,
  onRetry,
  title = '加载失败',
}: {
  error: unknown
  onRetry?: () => void
  title?: string
}) {
  const requestId = requestIdOf(error)

  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-state-line bg-page-card px-6 py-14 text-center">
      <span className="flex size-12 items-center justify-center rounded-full bg-state-danger/10">
        <AlertTriangle className="size-6 text-state-danger" aria-hidden />
      </span>
      <h3 className="text-subheading text-ink-900">{title}</h3>
      <p className="max-w-md text-sm text-ink-600">{toMessage(error)}</p>
      {requestId ? (
        <p className="select-all text-xs text-ink-400" title="反馈问题时可提供该请求号">
          请求号：{requestId}
        </p>
      ) : null}
      {onRetry ? (
        <Button variant="secondary" size="sm" icon={<RotateCcw className="size-4" aria-hidden />} onClick={onRetry}>
          重新加载
        </Button>
      ) : null}
    </div>
  )
}
