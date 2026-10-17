import { useMemo, useState } from 'react'
import { Search, Timer, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useProducts } from '../hooks/queries/useProducts'
import { useActivities } from '../hooks/queries/useActivities'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { ProductCard } from '../components/product/ProductCard'
import { ProductCardSkeleton } from '../components/ui/Skeleton'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Pagination } from '../components/ui/Pagination'
import { Button } from '../components/ui/Button'
import { formatRemaining, formatPrice } from '../lib/format'
import type { ActivityItem } from '../types/activity'

const PAGE_SIZE = 12

/** 首页/商品列表：真实分页 + 关键词搜索（300ms 防抖）+ 三态 + 进行中活动促销条。 */
export default function ProductListPage() {
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const debouncedKeyword = useDebouncedValue(keyword, 300)

  const productsQuery = useProducts({ page, pageSize: PAGE_SIZE, keyword: debouncedKeyword })
  const activitiesQuery = useActivities()

  const activeActivity = useMemo(() => {
    const items = activitiesQuery.data ?? []
    return items.find((item) => item.status === 'RUNNING') ?? null
  }, [activitiesQuery.data])

  const activityByProduct = useMemo(() => {
    const map = new Map<string, ActivityItem>()
    ;(activitiesQuery.data ?? []).forEach((item) => {
      if (item.status === 'RUNNING') {
        map.set(item.productId, item)
      }
    })
    return map
  }, [activitiesQuery.data])

  const onSearchChange = (value: string) => {
    setKeyword(value)
    setPage(1)
  }

  const data = productsQuery.data
  const total = data?.total ?? 0

  return (
    <div className="pb-12">
      <section className="border-b border-state-line bg-white">
        <div className="container-page flex flex-col gap-5 py-8 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-xl">
            <h1 className="text-heading text-ink-900">整点开抢 · 好物限时秒杀</h1>
            <p className="mt-2 text-sm leading-6 text-ink-600">
              库存与价格均来自实时服务，下单成功即进入排队；一人一件，抢完即止。
            </p>
          </div>

          {activeActivity ? (
            <Link
              to={`/activities/${activeActivity.activityId}`}
              className="flex items-center gap-4 rounded-xl border border-brand/20 bg-brand-soft px-5 py-4 transition-colors duration-150 hover:border-brand focus-ring"
            >
              <Timer className="size-6 text-brand" aria-hidden />
              <div>
                <p className="text-xs text-ink-600">正在进行</p>
                <p className="text-sm font-semibold text-ink-900">
                  {activeActivity.activityName ?? '限时秒杀活动'}
                </p>
              </div>
              <div className="ml-2 text-right">
                <p className="tnum text-sm font-bold text-brand">
                  {formatPrice(activeActivity.seckillPrice)}
                </p>
                <p className="tnum text-xs text-ink-600">
                  剩 {formatRemaining(activeActivity.endTime)} 结束
                </p>
              </div>
            </Link>
          ) : (
            <p className="text-sm text-ink-400">当前没有进行中的活动，先看看在售好物。</p>
          )}
        </div>
      </section>

      <div className="container-page pt-6">
        <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-400" aria-hidden />
            <input
              value={keyword}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="搜索商品名称，例如：耳机、咖啡机"
              aria-label="搜索商品"
              className="h-11 w-full rounded-md border border-state-line bg-white pl-9 pr-9 text-sm text-ink-900 outline-none transition-colors duration-150 placeholder:text-ink-400 focus:border-brand"
            />
            {keyword ? (
              <button
                type="button"
                aria-label="清空搜索"
                onClick={() => onSearchChange('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer rounded p-1 text-ink-400 transition-colors duration-150 hover:text-ink-900 focus-ring"
              >
                <X className="size-4" aria-hidden />
              </button>
            ) : null}
          </div>

          <div className="flex items-center gap-3 text-sm text-ink-600">
            <span className="tnum">共 {total} 件商品</span>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => productsQuery.refetch()}
              loading={productsQuery.isFetching}
            >
              刷新
            </Button>
          </div>
        </div>

        {productsQuery.isPending ? (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 8 }).map((_, index) => (
              <ProductCardSkeleton key={index} />
            ))}
          </div>
        ) : productsQuery.isError ? (
          <ErrorState error={productsQuery.error} onRetry={() => productsQuery.refetch()} />
        ) : data && data.records.length > 0 ? (
          <>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {data.records.map((product) => (
                <ProductCard
                  key={product.productId}
                  product={product}
                  activity={activityByProduct.get(product.productId)}
                />
              ))}
            </div>
            <div className="mt-8">
              <Pagination page={data.page} pages={data.pages} onChange={setPage} />
            </div>
          </>
        ) : (
          <EmptyState
            title={debouncedKeyword ? '没有匹配的商品' : '暂时没有在售商品'}
            description={
              debouncedKeyword
                ? `没有找到与「${debouncedKeyword}」相关的商品，换个关键词试试。`
                : '管理员上架商品后，这里会立即展示。'
            }
            action={
              debouncedKeyword ? (
                <Button variant="secondary" size="sm" onClick={() => onSearchChange('')}>
                  清空搜索
                </Button>
              ) : null
            }
          />
        )}
      </div>
    </div>
  )
}
