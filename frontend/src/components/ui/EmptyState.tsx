import type { ReactNode } from 'react'
import { PackageOpen } from 'lucide-react'

/** 空状态：中性图标底衬 + 一句说明 + 一个明确下一步（红色只留给真正的促销与主操作）。 */
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
      <div className="flex size-12 items-center justify-center rounded-full bg-page text-ink-600">
        {icon ?? <PackageOpen className="size-6" aria-hidden />}
      </div>
      <h3 className="text-subheading text-ink-900">{title}</h3>
      {description ? <p className="max-w-md text-sm leading-6 text-ink-600">{description}</p> : null}
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  )
}
