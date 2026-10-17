import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Boxes, ShieldCheck, Timer, Truck } from 'lucide-react'
import { useProduct } from '../hooks/queries/useProducts'
import { useActivities } from '../hooks/queries/useActivities'
import { ProductImage } from '../components/product/ProductImage'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Skeleton } from '../components/ui/Skeleton'
import { ErrorState } from '../components/ui/ErrorState'
import { formatDateTime, formatPrice } from '../lib/format'

/** 商品详情：展示真实价格/库存/描述，并引导到关联的秒杀活动（若有）。 */
export default function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>()
  const productQuery = useProduct(productId)
  const activitiesQuery = useActivities()

  const activity = (activitiesQuery.data ?? []).find((item) => item.productId === productId)
  const product = productQuery.data

  if (productQuery.isPending) {
    return (
      <div className="container-page grid gap-8 py-10 lg:grid-cols-2">
        <Skeleton className="aspect-[4/3] w-full rounded-xl" />
        <div className="space-y-4">
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-6 w-1/3" />
          <Skeleton className="h-24 w-full" />
        </div>
      </div>
    )
  }

  if (productQuery.isError) {
    return (
      <div className="container-page py-10">
        <ErrorState
          error={productQuery.error}
          title="商品加载失败"
          onRetry={() => productQuery.refetch()}
        />
      </div>
    )
  }

  if (!product) {
    return null
  }

  return (
    <div className="container-page py-8">
      <Link
        to="/products"
        className="mb-5 inline-flex items-center gap-1.5 text-sm text-ink-600 transition-colors duration-150 hover:text-brand focus-ring"
      >
        <ArrowLeft className="size-4" aria-hidden />
        返回商品列表
      </Link>

      <div className="grid gap-8 lg:grid-cols-[1.05fr_1fr]">
        <div className="card overflow-hidden">
          <ProductImage src={product.imageUrl} alt={product.name} ratio="aspect-[4/3]" />
        </div>

        <div className="flex flex-col gap-5">
          <div>
            <div className="mb-2 flex flex-wrap gap-2">
              <Badge tone={product.status === 'ON_SHELF' ? 'success' : 'neutral'}>
                {product.status === 'ON_SHELF' ? '在售' : '已下架'}
              </Badge>
              {activity ? <Badge tone="brand">有秒杀活动</Badge> : null}
            </div>
            <h1 className="text-heading text-ink-900">{product.name}</h1>
            <p className="mt-3 text-sm leading-6 text-ink-600">
              {product.description ?? '这件商品还没有补充描述。'}
            </p>
          </div>

          <div className="rounded-xl border border-state-line bg-page-subtle p-5">
            <p className="text-xs text-ink-600">商品原价</p>
            <p className="tnum mt-1 text-3xl font-bold text-ink-900">
              {formatPrice(product.originalPrice)}
            </p>
            <div className="mt-3 flex items-center gap-1.5 text-sm text-ink-600">
              <Boxes className="size-4" aria-hidden />
              <span className="tnum">
                库存 {product.availableStock ?? '—'} / {product.totalStock ?? '—'} 件
              </span>
            </div>
          </div>

          {activity ? (
            <Card
              title={
                <span className="flex items-center gap-2">
                  <Timer className="size-4 text-brand" aria-hidden />
                  {activity.activityName ?? '限时秒杀'}
                </span>
              }
              extra={<Badge tone="brand">{activity.status}</Badge>}
            >
              <div className="flex items-end justify-between gap-4">
                <div>
                  <p className="text-xs text-ink-600">秒杀价</p>
                  <p className="tnum text-2xl font-bold text-brand">
                    {formatPrice(activity.seckillPrice)}
                  </p>
                  <p className="tnum mt-1 text-xs text-ink-600">
                    {formatDateTime(activity.startTime)} — {formatDateTime(activity.endTime)}
                  </p>
                </div>
                <Link to={`/activities/${activity.activityId}`}>
                  <Button>去秒杀</Button>
                </Link>
              </div>
            </Card>
          ) : (
            <p className="rounded-lg bg-page px-4 py-3 text-sm text-ink-600">
              该商品当前没有进行中的秒杀活动。
            </p>
          )}

          <ul className="grid gap-3 text-sm text-ink-600 sm:grid-cols-2">
            <li className="flex items-center gap-2">
              <ShieldCheck className="size-4 text-state-success" aria-hidden />
              库存与订单均由服务端实时校验
            </li>
            <li className="flex items-center gap-2">
              <Truck className="size-4 text-state-info" aria-hidden />
              秒杀成功后可在「我的订单」查看排队结果
            </li>
          </ul>
        </div>
      </div>
    </div>
  )
}
