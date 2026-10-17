import { Link } from 'react-router-dom'
import { ArrowRight, Boxes } from 'lucide-react'
import { Badge } from '../ui/Badge'
import { ProductImage } from './ProductImage'
import { formatPrice } from '../../lib/format'
import { cn } from '../../lib/cn'
import type { Product } from '../../types/product'
import type { ActivityItem } from '../../types/activity'

/** 折扣角标：只有原价与秒杀价都存在且确实更低时才展示，不臆造优惠。 */
function discountLabel(price: number | null | undefined, original: number | null | undefined): string | null {
  if (price == null || original == null || original <= 0 || price >= original) {
    return null
  }
  const percent = Math.round((1 - price / original) * 10 * 10) / 10
  return `${percent % 1 === 0 ? percent.toFixed(0) : percent.toFixed(1)} 折`
}

/** 商品卡片：有进行中活动时展示秒杀价并跳活动页，否则跳商品详情。 */
export function ProductCard({ product, activity }: { product: Product; activity?: ActivityItem }) {
  const soldOut = product.availableStock !== null && product.availableStock <= 0
  const target = activity ? `/activities/${activity.activityId}` : `/products/${product.productId}`
  const seckillPrice = activity?.seckillPrice ?? null
  const discount = discountLabel(seckillPrice, product.originalPrice)

  return (
    <article className="card card-hover group flex flex-col overflow-hidden">
      <Link to={target} className="relative block focus-ring" aria-label={`查看 ${product.name}`}>
        <ProductImage src={product.imageUrl} alt={product.name} ratio="aspect-square" />
        <div className="pointer-events-none absolute inset-x-2.5 top-2.5 flex items-start justify-between gap-1.5">
          <div className="flex flex-col items-start gap-1.5">
            {activity ? (
              <Badge tone="brand" className="border-transparent bg-white/95 shadow-sm backdrop-blur-[2px]">
                限时秒杀
              </Badge>
            ) : null}
          </div>
          {soldOut ? (
            <Badge tone="neutral" className="border-transparent bg-ink-900/75 text-white shadow-sm">
              已售罄
            </Badge>
          ) : null}
        </div>
        {discount ? (
          <span className="tnum pointer-events-none absolute bottom-2.5 left-2.5 rounded-md border border-brand/20 bg-white/95 px-1.5 py-0.5 text-[11px] font-semibold text-brand shadow-sm">
            {discount}
          </span>
        ) : null}
      </Link>

      <div className="flex flex-1 flex-col gap-2 p-3 sm:gap-2.5 sm:p-4">
        <Link
          to={target}
          className="line-clamp-2 min-h-[2.75rem] text-sm font-semibold leading-[1.375rem] text-ink-900 transition-colors duration-150 hover:text-brand focus-ring sm:text-[15px] sm:leading-6"
        >
          {product.name}
        </Link>

        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          {seckillPrice != null ? (
            <>
              <span className="tnum text-lg font-extrabold leading-none text-brand sm:text-xl">
                {formatPrice(seckillPrice)}
              </span>
              <span className="tnum text-[11px] text-ink-400 line-through sm:text-xs">
                {formatPrice(product.originalPrice)}
              </span>
            </>
          ) : (
            <span className="tnum text-lg font-extrabold leading-none text-ink-900 sm:text-xl">
              {formatPrice(product.originalPrice)}
            </span>
          )}
        </div>

        <div className="flex items-center gap-1 text-[11px] text-ink-600 sm:text-xs">
          <Boxes className="size-3.5 shrink-0" aria-hidden />
          <span className="tnum truncate">
            剩余 {product.availableStock ?? '—'} / {product.totalStock ?? '—'} 件
          </span>
        </div>

        <Link
          to={target}
          className={cn(
            'mt-auto flex h-9 items-center justify-center gap-1 rounded-full text-xs font-semibold transition-colors duration-150 focus-ring sm:text-sm',
            soldOut
              ? 'bg-page text-ink-400 hover:bg-page'
              : activity
                ? 'bg-brand text-white hover:bg-brand-dark'
                : 'border border-state-line bg-white text-ink-900 hover:border-brand hover:text-brand',
          )}
        >
          {soldOut ? '已售罄' : activity ? '去抢购' : '查看详情'}
          <ArrowRight className="size-3.5 sm:size-4" aria-hidden />
        </Link>
      </div>
    </article>
  )
}
