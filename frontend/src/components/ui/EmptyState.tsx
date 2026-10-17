import type { ReactNode } from 'react'
import { PackageOpen } from 'lucide-react'

/** 空状态：一个图标 + 一句说明 + 一个明确下一步（遵循 baseline-ui 的"单一行动"）。 */
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
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-state-line bg-page-card px-6 py-14 text-center sm:py-16">
      <div className="flex size-12 items-center justify-center rounded-full bg-brand-soft text-brand">
        {icon ?? <PackageOpen className="size-6" aria-hidden />}
      </div>
      <h3 className="text-subheading text-ink-900">{title}</h3>
      {description ? <p className="max-w-md text-sm leading-6 text-ink-600">{description}</p> : null}
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  )
}
