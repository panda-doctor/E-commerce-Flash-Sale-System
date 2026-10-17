import { Crown } from 'lucide-react'
import { cn } from '../../lib/cn'
import { useRank } from '../../hooks/queries/useRank'
import { Skeleton } from '../ui/Skeleton'
import { EmptyState } from '../ui/EmptyState'

/** 抢购榜（公开读投影）：只展示后端返回的名次与脱敏后的用户标识。 */
export function RankList({ activityId }: { activityId: string }) {
  const rankQuery = useRank(activityId)

  if (rankQuery.isPending) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 3 }).map((_, index) => (
          <Skeleton key={index} className="h-10 w-full" />
        ))}
      </div>
    )
  }

  if (rankQuery.isError) {
    return <p className="text-sm text-ink-600">榜单暂时不可用，稍后自动重试。</p>
  }

  const entries = rankQuery.data ?? []
  if (entries.length === 0) {
    return <EmptyState title="还没有人抢到" description="成为第一个抢到的人，榜单会实时更新。" />
  }

  return (
    <ol className="divide-y divide-state-line">
      {entries.map((entry) => (
        <li key={`${entry.rank}-${entry.orderNo ?? entry.userId}`} className="flex items-center gap-3 py-2.5">
          <span
            className={cn(
              'tnum flex size-7 items-center justify-center rounded-full text-xs font-bold',
              entry.rank === 1
                ? 'bg-ink-900 text-white'
                : 'bg-page text-ink-600',
            )}
          >
            {entry.rank}
          </span>
          <span className="tnum flex-1 truncate text-sm text-ink-900">
            用户 {String(entry.userId).slice(-4).padStart(4, '0')}
          </span>
          <span className="tnum truncate text-xs text-ink-600">{entry.orderNo ?? '—'}</span>
          {entry.rank === 1 ? <Crown className="size-4 text-state-warning" aria-hidden /> : null}
        </li>
      ))}
    </ol>
  )
}
