import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '../../lib/cn'

/** 分页器：页码窗口最多 5 个，避免长列表把导航撑爆。 */
export function Pagination({
  page,
  pages,
  onChange,
}: {
  page: number
  pages: number
  onChange: (page: number) => void
}) {
  if (pages <= 1) {
    return null
  }

  const start = Math.max(1, Math.min(page - 2, pages - 4))
  const end = Math.min(pages, start + 4)
  const items = Array.from({ length: end - start + 1 }, (_, index) => start + index)

  const navButton = (target: number, disabled: boolean, label: string, icon: React.ReactNode) => (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(target)}
      className={cn(
        'flex size-9 items-center justify-center rounded-md border border-state-line bg-white text-ink-600 transition-colors duration-150',
        disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer hover:border-brand hover:text-brand',
      )}
    >
      {icon}
    </button>
  )

  return (
    <nav className="flex items-center justify-center gap-2" aria-label="分页">
      {navButton(page - 1, page <= 1, '上一页', <ChevronLeft className="size-4" aria-hidden />)}
      {items.map((item) => (
        <button
          key={item}
          type="button"
          aria-current={item === page ? 'page' : undefined}
          onClick={() => onChange(item)}
          className={cn(
            'size-9 cursor-pointer rounded-md border text-sm font-medium transition-colors duration-150',
            item === page
              ? 'border-brand bg-brand text-white'
              : 'border-state-line bg-white text-ink-600 hover:border-brand hover:text-brand',
          )}
        >
          {item}
        </button>
      ))}
      {navButton(page + 1, page >= pages, '下一页', <ChevronRight className="size-4" aria-hidden />)}
    </nav>
  )
}
