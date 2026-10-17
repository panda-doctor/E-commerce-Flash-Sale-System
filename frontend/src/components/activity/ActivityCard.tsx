import { Link } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { Badge } from '../ui/Badge'
import { ProductImage } from '../product/ProductImage'
import { Countdown } from './Countdown'
import { StockBar } from './StockBar'
import { PHASE_TEXT, PHASE_TONE, phaseOfItem } from '../../lib/activityPhase'
import { formatDateTime, formatPrice } from '../../lib/format'
import type { ActivityItem } from '../../types/activity'

/** 活动卡片：阶段徽章 + 秒杀价 + 倒计时 + 已抢进度。 */
export function ActivityCard({ item, onRefresh }: { item: ActivityItem; onRefresh?: () => void }) {
  const phase = phaseOfItem(item)
  const isUpcoming = phase === 'NOT_STARTED'
  const ended = phase === 'ENDED' || phase === 'SOLD_OUT' || phase === 'CANCELLED'

  return (
    <article className="group flex flex-col overflow-hidden rounded-2xl border border-state-line bg-page-card shadow-card transition-[border-color,box-shadow,transform] duration-200 hover:-translate-y-0.5 hover:border-state-hover hover:shadow-card-hover motion-reduce:transform-none">
      <Link to={`/activities/${item.activityId}`} className="relative block focus-ring">
        <ProductImage src={item.productImage} alt={item.productName ?? '活动商品'} ratio="aspect-[4/3]" />
        <span className="pointer-events-none absolute left-2.5 top-2.5">
          <Badge tone={PHASE_TONE[phase]} className="shadow-sm">
            {PHASE_TEXT[phase]}
          </Badge>
        </span>
      </Link>

      <div className="flex flex-1 flex-col gap-3 p-3.5 sm:p-4">
        <div className="min-w-0">
          <h3 className="truncate text-sm font-semibold text-ink-900 sm:text-[15px]">
            {item.activityName ?? '限时秒杀'}
          </h3>
          <p className="mt-0.5 truncate text-xs text-ink-600">
            {item.productName ?? '商品信息加载中'}
          </p>
        </div>

        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          <span className="tnum text-xl font-extrabold leading-none text-brand sm:text-[22px]">
            {formatPrice(item.seckillPrice)}
          </span>
          {item.seckillPrice != null &&
          item.originalPrice != null &&
          item.seckillPrice < item.originalPrice ? (
            <span className="tnum text-[11px] text-ink-600 line-through sm:text-xs">
              {formatPrice(item.originalPrice)}
            </span>
          ) : null}
        </div>

        <StockBar stock={item.stock} totalStock={item.totalStock} />

        <div className="flex items-center justify-between gap-2">
          <span className="rounded-full border border-brand/15 bg-brand-tint px-2 py-1">
            <Countdown
              target={isUpcoming ? item.startTime : item.endTime}
              label={isUpcoming ? '距开始' : '距结束'}
              onFinish={onRefresh}
            />
          </span>
          <span className="tnum truncate text-[11px] text-ink-600">
            {formatDateTime(item.startTime)} 起
          </span>
        </div>

        <Link
          to={`/activities/${item.activityId}`}
          className={
            ended
              ? 'mt-auto flex h-9 cursor-default items-center justify-center gap-1 rounded-xl bg-page-subtle text-sm font-semibold text-ink-400 focus-ring'
              : 'mt-auto flex h-9 items-center justify-center gap-1 rounded-xl bg-brand-solid text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-brand-dark focus-ring'
          }
        >
          {ended ? '查看活动' : '进入活动'}
          <ArrowRight className="size-4" aria-hidden />
        </Link>
      </div>
    </article>
  )
}
