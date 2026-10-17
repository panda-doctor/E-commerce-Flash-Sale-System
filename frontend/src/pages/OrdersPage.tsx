import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, ReceiptText, RefreshCw } from 'lucide-react'
import { useMyOrders } from '../hooks/queries/useOrders'
import { useAuth } from '../stores/auth'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Skeleton } from '../components/ui/Skeleton'
import { OrderStatusBadge } from '../components/order/OrderStatusBadge'
import { formatDateTime } from '../lib/format'
import type { OrderStatus } from '../types/order'

const FILTERS: Array<{ key: 'ALL' | OrderStatus; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'QUEUING', label: '排队中' },
  { key: 'CREATED', label: '已创建' },
  { key: 'CREATE_FAILED', label: '创建失败' },
]

/** 我的订单：真实接口 + 状态筛选 + 排队中自动轮询（全部终态后自动停）。 */
export default function OrdersPage() {
  const { user } = useAuth()
  const [filter, setFilter] = useState<'ALL' | OrderStatus>('ALL')
  const ordersQuery = useMyOrders(Boolean(user), { pollWhileQueuing: true })

  const orders = useMemo(() => ordersQuery.data ?? [], [ordersQuery.data])
  const filtered = useMemo(
    () => (filter === 'ALL' ? orders : orders.filter((order) => order.status === filter)),
    [orders, filter],
  )

  // 各状态真实计数（不臆造）：只统计后端已返回的订单
  const counts = useMemo(() => {
    const result: Record<string, number> = { ALL: orders.length }
    for (const order of orders) {
      result[order.status] = (result[order.status] ?? 0) + 1
    }
    return result
  }, [orders])

  return (
    <div className="container-page py-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <span className="chip">账户中心</span>
          <h1 className="mt-2 text-heading text-ink-900">我的订单</h1>
          <p className="mt-1.5 text-sm text-ink-600">
            共 {orders.length} 笔秒杀订单，排队中的订单会自动刷新结果。
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          className="rounded-full"
          icon={<RefreshCw className="size-4" aria-hidden />}
          loading={ordersQuery.isFetching}
          onClick={() => ordersQuery.refetch()}
        >
          刷新
        </Button>
      </div>

      {ordersQuery.pollingStopped ? (
        <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg bg-brand-soft px-4 py-2.5 text-xs text-ink-600">
          自动刷新已停止（超时或连续失败），排队中的订单可能仍在处理。
          <Button size="sm" variant="secondary" loading={ordersQuery.isFetching} onClick={() => ordersQuery.refetch()}>
            手动刷新
          </Button>
        </div>
      ) : null}

      <div className="mt-5 flex flex-wrap gap-2" role="tablist" aria-label="订单筛选">
        {FILTERS.map((item) => {
          const count = counts[item.key] ?? 0
          return (
            <button
              key={item.key}
              type="button"
              role="tab"
              aria-selected={filter === item.key}
              onClick={() => setFilter(item.key)}
              className={
                filter === item.key
                  ? 'cursor-pointer rounded-full bg-brand px-4 py-1.5 text-sm font-semibold text-white'
                  : 'cursor-pointer rounded-full border border-state-line bg-white px-4 py-1.5 text-sm text-ink-600 transition-colors duration-150 hover:border-brand hover:text-brand'
              }
            >
              {item.label}
              <span
                className={
                  filter === item.key
                    ? 'tnum ml-1.5 text-xs font-medium text-white/80'
                    : 'tnum ml-1.5 text-xs font-medium text-ink-400'
                }
              >
                {count}
              </span>
            </button>
          )
        })}
      </div>

      <div className="mt-6">
        {ordersQuery.isPending ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-20 w-full rounded-xl" />
            ))}
          </div>
        ) : ordersQuery.isError ? (
          <ErrorState error={ordersQuery.error} onRetry={() => ordersQuery.refetch()} />
        ) : filtered.length === 0 ? (
          <EmptyState
            title={filter === 'ALL' ? '还没有秒杀订单' : '这个状态下没有订单'}
            description="去活动页参与秒杀，成功后会在这里看到排队与最终结果。"
            icon={<ReceiptText className="size-9" aria-hidden />}
            action={
              <Link to="/activities">
                <Button size="sm">去看看秒杀活动</Button>
              </Link>
            }
          />
        ) : (
          <ul className="space-y-3">
            {filtered.map((order) => (
              <li key={order.orderNo}>
                <Link
                  to={`/orders/${encodeURIComponent(order.orderNo)}`}
                  className="card card-hover flex flex-col gap-3 p-4 focus-ring sm:flex-row sm:items-center sm:gap-5 sm:p-5"
                >
                  <div className="flex-1">
                    <p className="tnum text-sm font-semibold text-ink-900">{order.orderNo}</p>
                    <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-600">
                      <span className="tnum">活动 {order.activityId}</span>
                      <span className="tnum">商品 {order.productId}</span>
                    </div>
                    <p className="tnum mt-1 text-xs text-ink-400">
                      下单时间 {formatDateTime(order.createdAt)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <OrderStatusBadge status={order.status} />
                    <span className="flex size-8 items-center justify-center rounded-full bg-page text-ink-400">
                      <ArrowRight className="size-4" aria-hidden />
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
