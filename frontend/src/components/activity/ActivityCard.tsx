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

  return (
    <article className="card flex flex-col overflow-hidden transition-shadow duration-200 hover:shadow-lg">
      <Link to={`/activities/${item.activityId}`} className="block focus-ring">
        <ProductImage src={item.productImage} alt={item.productName ?? '活动商品'} />
      </Link>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <h3 className="truncate text-[15px] font-semibold text-ink-900">
              {item.activityName ?? '限时秒杀'}
            </h3>
            <p className="mt-0.5 truncate text-xs text-ink-600">
              {item.productName ?? '商品信息加载中'}
            </p>
          </div>
          <Badge tone={PHASE_TONE[phase]}>{PHASE_TEXT[phase]}</Badge>
        </div>

        <div className="flex items-baseline gap-2">
          <span className="tnum text-xl font-bold text-brand">{formatPrice(item.seckillPrice)}</span>
          <span className="tnum text-xs text-ink-400 line-through">
            {formatPrice(item.originalPrice)}
          </span>
        </div>

        <StockBar stock={item.stock} totalStock={item.totalStock} />

        {isUpcoming ? (
          <Countdown target={item.startTime} label="距开始" onFinish={onRefresh} />
        ) : (
          <Countdown target={item.endTime} label="距结束" onFinish={onRefresh} />
        )}

        <p className="tnum text-xs text-ink-400">
          {formatDateTime(item.startTime)} — {formatDateTime(item.endTime)}
        </p>

        <Link
          to={`/activities/${item.activityId}`}
          className="mt-auto flex h-9 items-center justify-center gap-1.5 rounded-md bg-brand text-sm font-semibold text-white transition-colors duration-150 hover:bg-brand-dark focus-ring"
        >
          进入活动
          <ArrowRight className="size-4" aria-hidden />
        </Link>
      </div>
    </article>
  )
}
