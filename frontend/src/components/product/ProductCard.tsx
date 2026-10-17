import { Link } from 'react-router-dom'
import { ArrowRight, Boxes } from 'lucide-react'
import { Badge } from '../ui/Badge'
import { ProductImage } from './ProductImage'
import { formatPrice } from '../../lib/format'
import type { Product } from '../../types/product'
import type { ActivityItem } from '../../types/activity'

/** 商品卡片：有进行中活动时展示秒杀价并跳活动页，否则跳商品详情。 */
export function ProductCard({ product, activity }: { product: Product; activity?: ActivityItem }) {
  const soldOut = product.availableStock !== null && product.availableStock <= 0
  const target = activity ? `/activities/${activity.activityId}` : `/products/${product.productId}`

  return (
    <article className="card group flex flex-col overflow-hidden transition-shadow duration-200 hover:shadow-lg">
      <Link to={target} className="relative block focus-ring" aria-label={`查看 ${product.name}`}>
        <ProductImage src={product.imageUrl} alt={product.name} />
        <div className="absolute left-3 top-3 flex gap-1.5">
          {activity ? <Badge tone="brand">限时秒杀</Badge> : null}
          {soldOut ? <Badge tone="neutral">已售罄</Badge> : null}
        </div>
      </Link>

      <div className="flex flex-1 flex-col gap-2.5 p-4">
        <Link
          to={target}
          className="line-clamp-2 text-[15px] font-semibold leading-6 text-ink-900 transition-colors duration-150 hover:text-brand"
        >
          {product.name}
        </Link>

        <div className="flex items-baseline gap-2">
          {activity?.seckillPrice != null ? (
            <>
              <span className="tnum text-xl font-bold text-brand">
                {formatPrice(activity.seckillPrice)}
              </span>
              <span className="tnum text-xs text-ink-400 line-through">
                {formatPrice(product.originalPrice)}
              </span>
            </>
          ) : (
            <span className="tnum text-xl font-bold text-ink-900">
              {formatPrice(product.originalPrice)}
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 text-xs text-ink-600">
          <Boxes className="size-3.5" aria-hidden />
          <span className="tnum">
            剩余 {product.availableStock ?? '—'} / {product.totalStock ?? '—'} 件
          </span>
        </div>

        <Link
          to={target}
          className="mt-auto flex h-9 items-center justify-center gap-1.5 rounded-md border border-brand/30 bg-brand-soft text-sm font-semibold text-brand transition-colors duration-150 hover:bg-brand hover:text-white focus-ring"
        >
          {activity ? '去抢购' : '查看详情'}
          <ArrowRight className="size-4" aria-hidden />
        </Link>
      </div>
    </article>
  )
}
