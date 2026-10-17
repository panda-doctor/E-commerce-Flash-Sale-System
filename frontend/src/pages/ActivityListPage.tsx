import { useMemo, useState } from 'react'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Skeleton } from '../components/ui/Skeleton'
import { ActivityCard } from '../components/activity/ActivityCard'
import { useActivities } from '../hooks/queries/useActivities'
import { PHASE_TEXT, phaseOfItem, type Phase } from '../lib/activityPhase'

const FILTERS: Array<{ key: 'ALL' | Phase; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'IN_PROGRESS', label: '进行中' },
  { key: 'NOT_STARTED', label: '即将开始' },
  { key: 'SOLD_OUT', label: '已售罄' },
  { key: 'ENDED', label: '已结束' },
]

/** 秒杀活动列表：按前端推导的阶段筛选（不做假接口）。 */
export default function ActivityListPage() {
  const activitiesQuery = useActivities()
  const [filter, setFilter] = useState<'ALL' | Phase>('ALL')

  const items = useMemo(() => activitiesQuery.data ?? [], [activitiesQuery.data])
  const filtered = useMemo(() => {
    if (filter === 'ALL') {
      return items
    }
    return items.filter((item) => phaseOfItem(item) === filter)
  }, [items, filter])

  return (
    <div className="container-page py-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-heading text-ink-900">秒杀活动</h1>
          <p className="mt-1.5 text-sm text-ink-600">
            共 {items.length} 场活动，库存与阶段均由服务端数据实时推导。
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          loading={activitiesQuery.isFetching}
          onClick={() => activitiesQuery.refetch()}
        >
          刷新
        </Button>
      </div>

      <div className="mt-5 flex flex-wrap gap-2" role="tablist" aria-label="活动筛选">
        {FILTERS.map((item) => (
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
          </button>
        ))}
      </div>

      <div className="mt-6">
        {activitiesQuery.isPending ? (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="card overflow-hidden">
                <Skeleton className="aspect-[4/3] w-full rounded-none" />
                <div className="space-y-3 p-4">
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-4 w-1/3" />
                  <Skeleton className="h-9 w-full" />
                </div>
              </div>
            ))}
          </div>
        ) : activitiesQuery.isError ? (
          <ErrorState error={activitiesQuery.error} onRetry={() => activitiesQuery.refetch()} />
        ) : filtered.length === 0 ? (
          <EmptyState
            title={filter === 'ALL' ? '当前没有秒杀活动' : `没有${PHASE_TEXT[filter as Phase]}的活动`}
            description="活动由管理员创建并预热后展示在这里。"
            action={
              filter === 'ALL' ? null : (
                <Button variant="secondary" size="sm" onClick={() => setFilter('ALL')}>
                  查看全部活动
                </Button>
              )
            }
          />
        ) : (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {filtered.map((item) => (
              <ActivityCard key={item.activityId} item={item} onRefresh={() => activitiesQuery.refetch()} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
