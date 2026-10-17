import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Info, ShieldCheck, Trophy } from 'lucide-react'
import { useActivity } from '../hooks/queries/useActivities'
import { useProduct } from '../hooks/queries/useProducts'
import { ProductImage } from '../components/product/ProductImage'
import { Countdown } from '../components/activity/Countdown'
import { StockBar } from '../components/activity/StockBar'
import { SeckillPanel } from '../components/seckill/SeckillPanel'
import { RankList } from '../components/seckill/RankList'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { Skeleton } from '../components/ui/Skeleton'
import { ErrorState } from '../components/ui/ErrorState'
import { formatDateTime, formatPrice } from '../lib/format'
import { PHASE_TEXT, PHASE_TONE, resolvePhase } from '../lib/activityPhase'

/** 活动详情 / 秒杀主战场：左图右交易面板，下方榜单与规则说明。 */
export default function ActivityDetailPage() {
  const { activityId } = useParams<{ activityId: string }>()
  const activityQuery = useActivity(activityId)
  const activity = activityQuery.data
  const productQuery = useProduct(activity?.productId)
  const product = productQuery.data

  if (activityQuery.isPending) {
    return (
      <div className="container-page grid gap-8 py-10 lg:grid-cols-[1.05fr_1fr]">
        <Skeleton className="aspect-[4/3] w-full rounded-xl" />
        <div className="space-y-4">
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      </div>
    )
  }

  if (activityQuery.isError || !activity) {
    return (
      <div className="container-page py-10">
        <ErrorState
          error={activityQuery.error}
          title="活动加载失败"
          onRetry={() => activityQuery.refetch()}
        />
      </div>
    )
  }

  const phase = resolvePhase({
    status: activity.status,
    startTime: activity.startTime,
    endTime: activity.endTime,
    stock: activity.stock,
  })

  return (
    <div className="container-page py-8">
      <Link
        to="/activities"
        className="mb-5 inline-flex items-center gap-1.5 text-sm text-ink-600 transition-colors duration-150 hover:text-brand focus-ring"
      >
        <ArrowLeft className="size-4" aria-hidden />
        返回活动列表
      </Link>

      <div className="grid gap-8 lg:grid-cols-[1.05fr_1fr]">
        <div className="space-y-4">
          <div className="card overflow-hidden">
            <ProductImage
              src={product?.imageUrl ?? null}
              alt={product?.name ?? activity.activityName ?? '秒杀商品'}
              ratio="aspect-[4/3]"
            />
          </div>
          <div className="flex items-center gap-2 text-xs text-ink-600">
            <Info className="size-3.5" aria-hidden />
            <span>图片与库存来自商品服务；库存扣减与订单状态由后端实时校验。</span>
          </div>
        </div>

        <div className="space-y-5">
          <div>
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <Badge tone={PHASE_TONE[phase]}>{PHASE_TEXT[phase]}</Badge>
              {activity.limitPerUser ? <Badge tone="neutral">限购 {activity.limitPerUser} 件</Badge> : null}
            </div>
            <h1 className="text-heading text-ink-900">{activity.activityName ?? '限时秒杀'}</h1>
            {product ? (
              <Link
                to={`/products/${product.productId}`}
                className="mt-1.5 inline-block text-sm text-ink-600 transition-colors duration-150 hover:text-brand"
              >
                {product.name}
              </Link>
            ) : null}
          </div>

          <div className="rounded-xl border border-brand/20 bg-brand-soft p-5">
            <div className="flex items-end gap-3">
              <span className="tnum text-3xl font-bold text-brand">
                {formatPrice(activity.seckillPrice)}
              </span>
              {product?.originalPrice != null ? (
                <span className="tnum pb-1 text-sm text-ink-400 line-through">
                  {formatPrice(product.originalPrice)}
                </span>
              ) : null}
            </div>
            <p className="tnum mt-2 text-xs text-ink-600">
              {formatDateTime(activity.startTime)} — {formatDateTime(activity.endTime)}
            </p>
          </div>

          <Card>
            <div className="space-y-4">
              <StockBar stock={activity.stock} totalStock={activity.totalStock} />
              <Countdown
                target={phase === 'NOT_STARTED' ? activity.startTime : activity.endTime}
                label={phase === 'NOT_STARTED' ? '距开始' : '距结束'}
                onFinish={() => activityQuery.refetch()}
              />
              <SeckillPanel
                activityId={activity.activityId}
                productId={activity.productId}
                phase={phase}
                limitPerUser={activity.limitPerUser}
              />
            </div>
          </Card>

          <Card
            title={
              <span className="flex items-center gap-2">
                <Trophy className="size-4 text-state-warning" aria-hidden />
                抢购榜 TOP 10
              </span>
            }
          >
            <RankList activityId={activity.activityId} />
          </Card>

          <ul className="space-y-2 rounded-lg bg-page px-4 py-3 text-xs leading-5 text-ink-600">
            <li className="flex items-start gap-1.5">
              <ShieldCheck className="mt-0.5 size-3.5 shrink-0 text-state-success" aria-hidden />
              秒杀一次只成交一件；重复提交会返回「已抢过」，不会重复下单。
            </li>
            <li className="flex items-start gap-1.5">
              <Info className="mt-0.5 size-3.5 shrink-0 text-state-info" aria-hidden />
              提交成功后订单进入排队，本页会自动轮询结果，也可以稍后到「我的订单」查看。
            </li>
          </ul>
        </div>
      </div>
    </div>
  )
}
