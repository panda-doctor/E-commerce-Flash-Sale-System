import type { ReactNode } from 'react'
import { PackageOpen } from 'lucide-react'

export function EmptyState({
  title,
  description,
  action,
  icon,
}: {
  title: string
  description?: string
  action?: ReactNode
  icon?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-state-line bg-page-card px-6 py-16 text-center">
      <div className="text-ink-400">{icon ?? <PackageOpen className="size-9" aria-hidden />}</div>
      <h3 className="text-subheading text-ink-900">{title}</h3>
      {description ? <p className="max-w-md text-sm text-ink-600">{description}</p> : null}
      {action}
    </div>
  )
}
