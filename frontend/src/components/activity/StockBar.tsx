import { stockPercent } from '../../lib/format'

/** 已抢进度条：暖色轨道 + 橙红渐变填充，右侧给出剩余件数。 */
export function StockBar({
  stock,
  totalStock,
}: {
  stock: number | null | undefined
  totalStock: number | null | undefined
}) {
  const percent = stockPercent(stock, totalStock)
  const soldOut = stock !== null && stock !== undefined && stock <= 0
  const remaining = stock ?? null

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-xs">
        <span className="text-ink-600">
          已抢 <span className="tnum font-bold text-brand-dark">{percent}%</span>
        </span>
        <span className="tnum text-ink-600">
          {remaining === null ? '库存未知' : soldOut ? '已抢光' : `仅剩 ${remaining} 件`}
        </span>
      </div>
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-brand-soft"
        role="progressbar"
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="已抢进度"
      >
        <div
          className="h-full rounded-full bg-gradient-to-r from-brand-orange to-brand transition-[width] duration-300"
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  )
}
