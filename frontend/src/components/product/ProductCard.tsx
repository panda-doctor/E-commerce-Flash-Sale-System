import { Link } from 'react-router-dom'
import { ArrowRight, Boxes } from 'lucide-react'
import { Badge } from '../ui/Badge'
import { ProductImage } from './ProductImage'
import { formatPrice } from '../../lib/format'
import { cn } from '../../lib/cn'
import type { Product } from '../../types/product'
import type { ActivityItem } from '../../types/activity'

/**
 * 折扣角标：只有原价与秒杀价都存在、且优惠幅度 ≥5% 时才展示。
 * 差价很小（如 19.99 对 20.49）时算出的"0.2 折"会误导用户，因此不显示。
 */
function discountLabel(price: number | null | undefined, original: number | null | undefined): string | null {
  if (price == null || original == null || original <= 0 || price >= original) {
    return null
  }
  const saved = 1 - price / original
  if (saved < 0.05) {
    return null
  }
  const percent = Math.round(saved * 10 * 10) / 10
  return `${percent % 1 === 0 ? percent.toFixed(0) : percent.toFixed(1)} 折`
}

/** 商品卡片：有进行中活动时展示秒杀价并跳活动页，否则跳商品详情。 */
export function ProductCard({ product, activity }: { product: Product; activity?: ActivityItem }) {
  const soldOut = product.availableStock !== null && product.availableStock <= 0
  const target = activity ? `/activities/${activity.activityId}` : `/products/${product.productId}`
  const seckillPrice = activity?.seckillPrice ?? null
  const discount = discountLabel(seckillPrice, product.originalPrice)
  // 只有"活动价确实低于原价"时才展示划线原价，避免出现活动价比原价还贵的荒谬对照
  const showOriginalPrice =
    seckillPrice != null && product.originalPrice != null && seckillPrice < product.originalPrice

  return (
    <article className="group flex flex-col overflow-hidden rounded-2xl border border-state-line bg-page-card shadow-card transition-[border-color,box-shadow,transform] duration-200 hover:-translate-y-0.5 hover:border-state-hover hover:shadow-card-hover motion-reduce:transform-none">
      <Link to={target} className="relative block focus-ring" aria-label={`查看 ${product.name}`}>
        <ProductImage src={product.imageUrl} alt={product.name} ratio="aspect-square" />
        <div className="pointer-events-none absolute inset-x-2.5 top-2.5 flex items-start justify-between gap-1.5">
          <div className="flex flex-col items-start gap-1.5">
            {activity ? (
              <Badge tone="brand" className="border-brand/25 bg-brand-soft/95 shadow-sm">
                限时秒杀
              </Badge>
            ) : null}
          </div>
          {soldOut ? (
            <Badge tone="neutral" className="border-state-line bg-page-card/95 text-ink-600 shadow-sm">
              已售罄
            </Badge>
          ) : null}
        </div>
        {discount ? (
          <span className="tnum pointer-events-none absolute bottom-2.5 left-2.5 rounded-lg bg-brand-solid px-2 py-0.5 text-[11px] font-bold text-white shadow-sm">
            {discount}
          </span>
        ) : null}
      </Link>

      <div className="flex flex-1 flex-col gap-2.5 p-3.5 sm:p-4">
        <Link
          to={target}
          className="line-clamp-2 min-h-[2.75rem] text-sm font-semibold leading-[1.375rem] text-ink-900 focus-ring sm:text-[15px] sm:leading-6"
        >
          {product.name}
        </Link>

        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          {seckillPrice != null ? (
            <>
              <span className="text-[11px] font-medium text-brand-dark">秒杀价</span>
              <span className="tnum text-xl font-extrabold leading-none text-brand sm:text-[22px]">
                {formatPrice(seckillPrice)}
              </span>
              {showOriginalPrice ? (
                <span className="tnum text-[11px] text-ink-600 line-through sm:text-xs">
                  {formatPrice(product.originalPrice)}
                </span>
              ) : null}
            </>
          ) : (
            <span className="tnum text-xl font-extrabold leading-none text-ink-900 sm:text-[22px]">
              {formatPrice(product.originalPrice)}
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 text-[11px] text-ink-600 sm:text-xs">
          <Boxes className="size-3.5 shrink-0 text-brand-orange" aria-hidden />
          <span className="tnum truncate">
            剩余 {product.availableStock ?? '—'} / {product.totalStock ?? '—'} 件
          </span>
        </div>

        <Link
          to={target}
          className={cn(
            'mt-auto flex h-9 items-center justify-center gap-1 rounded-xl text-xs font-semibold transition-colors duration-150 focus-ring sm:text-sm',
            soldOut
              ? 'cursor-not-allowed bg-page-subtle text-ink-400'
              : activity
                ? 'bg-brand-solid text-white shadow-sm hover:bg-brand-dark'
                : 'border border-state-line bg-white text-ink-900 hover:border-state-hover hover:bg-brand-tint',
          )}
        >
          {soldOut ? '已售罄' : activity ? '去抢购' : '查看详情'}
          <ArrowRight className="size-3.5 sm:size-4" aria-hidden />
        </Link>
      </div>
    </article>
  )
}
