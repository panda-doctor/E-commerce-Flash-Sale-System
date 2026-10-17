import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '../../lib/cn'

/** 分页器：页码窗口最多 5 个；选中态使用品牌实心红（与主操作同一体系）。 */
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
        'flex size-9 items-center justify-center rounded-full border border-state-line bg-white text-ink-600 transition-colors duration-150 focus-ring',
        disabled
          ? 'cursor-not-allowed opacity-45'
          : 'cursor-pointer hover:border-state-hover hover:bg-brand-tint hover:text-ink-900',
      )}
    >
      {icon}
    </button>
  )

  return (
    <nav className="flex items-center justify-center gap-1.5 sm:gap-2" aria-label="分页">
      {navButton(page - 1, page <= 1, '上一页', <ChevronLeft className="size-4" aria-hidden />)}
      {items.map((item) => (
        <button
          key={item}
          type="button"
          aria-current={item === page ? 'page' : undefined}
          onClick={() => onChange(item)}
          className={cn(
            'tnum size-9 cursor-pointer rounded-full border text-sm font-semibold transition-colors duration-150 focus-ring',
            item === page
              ? 'border-brand-solid bg-brand-solid text-white'
              : 'border-state-line bg-white text-ink-600 hover:border-state-hover hover:text-ink-900',
          )}
        >
          {item}
        </button>
      ))}
      {navButton(page + 1, page >= pages, '下一页', <ChevronRight className="size-4" aria-hidden />)}
    </nav>
  )
}
