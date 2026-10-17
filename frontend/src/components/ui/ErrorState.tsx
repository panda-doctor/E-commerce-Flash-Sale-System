import { AlertTriangle, RotateCcw } from 'lucide-react'
import { Button } from './Button'
import { toMessage } from '../../lib/errors'

export function ErrorState({
  error,
  onRetry,
  title = '加载失败',
}: {
  error: unknown
  onRetry?: () => void
  title?: string
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-state-danger/20 bg-brand-soft/40 px-6 py-14 text-center">
      <AlertTriangle className="size-8 text-state-danger" aria-hidden />
      <h3 className="text-subheading text-ink-900">{title}</h3>
      <p className="max-w-md text-sm text-ink-600">{toMessage(error)}</p>
      {onRetry ? (
        <Button variant="secondary" size="sm" icon={<RotateCcw className="size-4" aria-hidden />} onClick={onRetry}>
          重新加载
        </Button>
      ) : null}
    </div>
  )
}
