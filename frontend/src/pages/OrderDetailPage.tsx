import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Loader2 } from 'lucide-react'
import { useOrderPolling } from '../hooks/queries/useOrders'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Skeleton } from '../components/ui/Skeleton'
import { ErrorState } from '../components/ui/ErrorState'
import { OrderStatusBadge, orderStatusText } from '../components/order/OrderStatusBadge'
import { formatDateTime, formatPrice } from '../lib/format'

/** 订单详情：QUEUING 时自动轮询（1.5s），终态即停；字段按真实返回渲染。 */
export default function OrderDetailPage() {
  const { orderNo } = useParams<{ orderNo: string }>()
  const orderQuery = useOrderPolling(orderNo)
  const order = orderQuery.data

  if (orderQuery.isPending) {
    return (
      <div className="container-page max-w-2xl space-y-4 py-10">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    )
  }

  if (orderQuery.isError || !order) {
    return (
      <div className="container-page max-w-2xl py-10">
        <ErrorState
          error={orderQuery.error}
          title="订单加载失败"
          onRetry={() => orderQuery.refetch()}
        />
      </div>
    )
  }

  const queuing = order.status === 'QUEUING'

  return (
    <div className="container-page max-w-2xl py-8">
      <Link
        to="/orders"
        className="mb-5 inline-flex items-center gap-1.5 text-sm text-ink-600 transition-colors duration-150 hover:text-brand focus-ring"
      >
        <ArrowLeft className="size-4" aria-hidden />
        返回订单列表
      </Link>

      <Card
        title="订单详情"
        extra={
          <span className="flex items-center gap-2">
            {queuing && orderQuery.isFetching ? (
              <Loader2 className="size-3.5 animate-spin text-ink-400" aria-hidden />
            ) : null}
            <OrderStatusBadge status={order.status} />
          </span>
        }
      >
        <dl className="space-y-4 text-sm">
          <div>
            <dt className="text-ink-600">订单号</dt>
            <dd className="tnum mt-0.5 break-all font-semibold text-ink-900">{order.orderNo}</dd>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-ink-600">活动 ID</dt>
              <dd className="tnum mt-0.5 text-ink-900">{order.activityId ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-ink-600">商品 ID</dt>
              <dd className="tnum mt-0.5 text-ink-900">{order.productId ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-ink-600">成交价</dt>
              <dd className="tnum mt-0.5 text-ink-900">{formatPrice(order.seckillPrice ?? null)}</dd>
            </div>
            <div>
              <dt className="text-ink-600">下单时间</dt>
              <dd className="tnum mt-0.5 text-ink-900">{formatDateTime(order.createdAt)}</dd>
            </div>
          </div>
        </dl>

        {queuing ? (
          <div className="mt-5 flex items-center gap-2 rounded-lg bg-brand-soft px-3 py-2.5 text-xs text-ink-600">
            <Loader2 className="size-3.5 animate-spin text-brand" aria-hidden />
            正在排队生成订单，页面会自动刷新（离开本页即停止轮询）。
          </div>
        ) : (
          <div className="mt-5 flex flex-wrap items-center gap-2">
            <Badge tone={order.status === 'CREATED' ? 'success' : 'neutral'}>
              {orderStatusText(order.status)}
            </Badge>
            <Button size="sm" variant="secondary" onClick={() => orderQuery.refetch()}>
              刷新状态
            </Button>
            <Link to="/orders">
              <Button size="sm" variant="ghost">
                查看全部订单
              </Button>
            </Link>
          </div>
        )}
      </Card>
    </div>
  )
}
