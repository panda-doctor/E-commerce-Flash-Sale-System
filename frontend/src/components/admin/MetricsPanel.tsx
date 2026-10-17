import { useQuery } from '@tanstack/react-query'
import { Activity, RefreshCw, Timer } from 'lucide-react'
import { fetchMetrics } from '../../api/admin'
import { useAdminToken } from '../../stores/adminToken'
import { queryKeys, tokenFingerprint } from '../../lib/queryKeys'
import { Button } from '../ui/Button'
import { Card } from '../ui/Card'
import { Skeleton } from '../ui/Skeleton'
import { ErrorState } from '../ui/ErrorState'

function toRow(metrics: {
  redisStock: number | null
  orderCount: number | null
  queuedMessageCount: number | null
  successCount: number | null
  duplicateRejectCount: number | null
  rateLimitRejectCount: number | null
  soldOutRejectCount: number | null
}) {
  return {
    redisStock: metrics.redisStock,
    orderCount: metrics.orderCount,
    queuedMessageCount: metrics.queuedMessageCount,
    successCount: metrics.successCount,
    duplicateRejectCount: metrics.duplicateRejectCount,
    rateLimitRejectCount: metrics.rateLimitRejectCount,
    soldOutRejectCount: metrics.soldOutRejectCount,
  }
}

const LABELS: Array<[keyof ReturnType<typeof toRow>, string]> = [
  ['redisStock', 'Redis 剩余库存'],
  ['orderCount', '已成交订单'],
  ['successCount', '成功响应数'],
  ['queuedMessageCount', '队列待消费'],
  ['soldOutRejectCount', '售罄拒绝'],
  ['duplicateRejectCount', '重复拒绝'],
  ['rateLimitRejectCount', '限流拒绝'],
]

/** 活动实时指标：5s 自动刷新（管理端需要看波动，不是缓存快照）。 */
export function MetricsPanel({ activityId }: { activityId: string }) {
  const { token } = useAdminToken()

  const metricsQuery = useQuery({
    // 键带令牌指纹：换管理员令牌后不复用上一位的指标缓存；无令牌时不发请求
    queryKey: queryKeys.adminMetrics(tokenFingerprint(token), activityId),
    queryFn: () => fetchMetrics(token, activityId),
    enabled: Boolean(token) && Boolean(activityId),
    refetchInterval: 5000,
    staleTime: 0,
  })

  if (!activityId) {
    return null
  }

  if (metricsQuery.isPending) {
    return (
      <Card title="实时指标">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-20 w-full" />
          ))}
        </div>
      </Card>
    )
  }

  if (metricsQuery.isError) {
    return (
      <Card title="实时指标">
        <ErrorState error={metricsQuery.error} onRetry={() => metricsQuery.refetch()} />
      </Card>
    )
  }

  const row = toRow(metricsQuery.data)

  return (
    <Card
      title={
        <span className="flex items-center gap-2">
          <Activity className="size-4 text-ink-400" aria-hidden />
          实时指标
        </span>
      }
      extra={
        <Button
          size="sm"
          variant="ghost"
          icon={<RefreshCw className="size-4" aria-hidden />}
          loading={metricsQuery.isFetching}
          onClick={() => metricsQuery.refetch()}
        >
          刷新
        </Button>
      }
    >
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {LABELS.map(([key, label]) => (
          <div key={key} className="rounded-lg border border-state-line bg-page-subtle p-3">
            <p className="text-xs text-ink-600">{label}</p>
            <p className="tnum mt-1 text-xl font-bold text-ink-900">{row[key] ?? '—'}</p>
          </div>
        ))}
      </div>
      <p className="mt-3 flex items-center gap-1.5 text-xs text-ink-600">
        <Timer className="size-3.5" aria-hidden />
        每 5 秒自动刷新；数据来自管理端指标接口（Redis 投影 + 队列长度）。
      </p>
    </Card>
  )
}
