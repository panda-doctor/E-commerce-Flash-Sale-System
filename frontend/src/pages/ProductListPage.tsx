import { useEffect, useMemo, useState } from 'react'
import {
  ArrowRight,
  Flame,
  ReceiptText,
  Search,
  ShieldCheck,
  Timer,
  X,
} from 'lucide-react'
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
import { phaseOfItem } from '../lib/activityPhase'
import type { ActivityItem } from '../types/activity'

const PAGE_SIZE = 12

/** 首页/商品列表：真实分页 + 关键词搜索（300ms 防抖）+ 三态 + 进行中活动促销条。 */
export default function ProductListPage() {
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const debouncedKeyword = useDebouncedValue(keyword, 300)

  const productsQuery = useProducts({ page, pageSize: PAGE_SIZE, keyword: debouncedKeyword })
  const activitiesQuery = useActivities()

  // 阶段必须与活动列表一致：后端 status 是创建时快照，不会随时间自动翻转，
  // 只有按时间窗推导才不会出现"活动卡片显示进行中、首页却说没有活动"的割裂。
  const activeActivity = useMemo(() => {
    const items = activitiesQuery.data ?? []
    return items.find((item) => phaseOfItem(item) === 'IN_PROGRESS') ?? null
  }, [activitiesQuery.data])

  const activityByProduct = useMemo(() => {
    const map = new Map<string, ActivityItem>()
    ;(activitiesQuery.data ?? []).forEach((item) => {
      if (phaseOfItem(item) === 'IN_PROGRESS') {
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

  // 页码越界兜底：total>0 但当前页为空（多半是商品刚下架或数据减少），自动退回最后一页
  const lastPage = data && data.pages > 0 ? data.pages : 1
  const pageOutOfRange = Boolean(data) && total > 0 && (data?.records.length ?? 0) === 0 && page > lastPage

  useEffect(() => {
    if (pageOutOfRange) {
      setPage(lastPage)
    }
  }, [pageOutOfRange, lastPage])

  return (
    <div className="pb-12">
      <section className="border-b border-state-line bg-white">
        <div className="container-page grid gap-8 py-8 lg:grid-cols-[1.15fr_0.85fr] lg:items-center lg:py-12">
          <div>
            <span className="chip border-brand/20 bg-brand-soft text-brand">
              <Flame className="size-3.5" aria-hidden />
              整点开抢 · 限时秒杀
            </span>
            <h1 className="mt-4 text-[28px] font-extrabold leading-[1.2] text-ink-900 sm:text-[36px] lg:text-[40px]">
              <span className="block">好物限时秒杀</span>
              <span className="block">抢到就是赚到</span>
            </h1>
            <p className="mt-3 max-w-lg text-sm leading-6 text-ink-600">
              库存与价格均来自实时服务，下单成功即进入排队；一人一件，抢完即止。
            </p>
            <ul className="mt-5 flex flex-wrap gap-x-5 gap-y-2 text-xs text-ink-600">
              <li className="flex items-center gap-1.5">
                <ShieldCheck className="size-3.5 text-state-success" aria-hidden />
                库存由服务端实时校验
              </li>
              <li className="flex items-center gap-1.5">
                <Timer className="size-3.5 text-brand" aria-hidden />
                倒计时以服务端时间为准
              </li>
              <li className="flex items-center gap-1.5">
                <ReceiptText className="size-3.5 text-state-info" aria-hidden />
                排队结果随时可查
              </li>
            </ul>
          </div>

          {activeActivity ? (
            <Link
              to={`/activities/${activeActivity.activityId}`}
              className="group block rounded-xl border border-brand/20 bg-brand-soft p-5 transition-colors duration-150 hover:border-brand/50 focus-ring sm:p-6"
            >
              <div className="flex items-center justify-between gap-3">
                <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand">
                  <span className="size-1.5 rounded-full bg-brand" aria-hidden />
                  正在进行
                </span>
                <span className="tnum text-xs text-ink-600">
                  剩 {formatRemaining(activeActivity.endTime)}
                </span>
              </div>
              <h2 className="mt-3 line-clamp-1 text-lg font-bold text-ink-900">
                {activeActivity.activityName ?? '限时秒杀活动'}
              </h2>
              <p className="mt-1 line-clamp-1 text-xs text-ink-600">
                {activeActivity.productName ?? '实时库存，先到先得'}
              </p>
              <div className="mt-5 flex items-end justify-between gap-3">
                <div>
                  <p className="text-[11px] text-ink-600">秒杀价</p>
                  <p className="tnum mt-0.5 text-3xl font-extrabold leading-none text-brand">
                    {formatPrice(activeActivity.seckillPrice)}
                  </p>
                </div>
                <span className="inline-flex h-9 items-center gap-1 rounded-full bg-brand px-4 text-sm font-semibold text-white transition-colors duration-150 group-hover:bg-brand-dark">
                  立即抢购
                  <ArrowRight className="size-4" aria-hidden />
                </span>
              </div>
            </Link>
          ) : (
            <div className="rounded-xl border border-dashed border-state-line bg-page-subtle px-5 py-8">
              <p className="text-sm font-semibold text-ink-900">
                当前没有进行中的活动，先看看在售好物。
              </p>
              <p className="mt-1.5 text-sm leading-6 text-ink-600">
                活动开始后，这里会展示实时秒杀价与倒计时。
              </p>
            </div>
          )}
        </div>
      </section>

      <div className="container-page pt-6">
        <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-md">
            <Search
              className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-ink-400"
              aria-hidden
            />
            <input
              value={keyword}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="搜索商品名称，例如：耳机、咖啡机"
              aria-label="搜索商品"
              className="h-11 w-full rounded-full border border-state-line bg-white pl-10 pr-10 text-sm text-ink-900 outline-none transition-[border-color,box-shadow] duration-150 placeholder:text-ink-400 hover:border-ink-400/40 focus:border-brand focus:ring-2 focus:ring-brand/15"
            />
            {keyword ? (
              <button
                type="button"
                aria-label="清空搜索"
                onClick={() => onSearchChange('')}
                className="absolute right-2.5 top-1/2 flex size-7 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-ink-400 transition-colors duration-150 hover:bg-page hover:text-ink-900 focus-ring"
              >
                <X className="size-4" aria-hidden />
              </button>
            ) : null}
          </div>

          <div className="flex items-center justify-between gap-3 text-sm text-ink-600 sm:justify-end">
            <span className="tnum">共 {total} 件商品</span>
            <Button
              size="sm"
              variant="secondary"
              className="rounded-full"
              onClick={() => productsQuery.refetch()}
              loading={productsQuery.isFetching}
            >
              刷新
            </Button>
          </div>
        </div>

        <div className="mb-4 flex items-baseline justify-between gap-3">
          <h2 className="text-subheading text-ink-900">在售好物</h2>
          {debouncedKeyword ? (
            <span className="truncate text-xs text-ink-400">“{debouncedKeyword}” 的搜索结果</span>
          ) : (
            <span className="text-xs text-ink-400">实时库存 · 售完即止</span>
          )}
        </div>

        {productsQuery.isPending ? (
          <div className="grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 8 }).map((_, index) => (
              <ProductCardSkeleton key={index} />
            ))}
          </div>
        ) : productsQuery.isError ? (
          <ErrorState error={productsQuery.error} onRetry={() => productsQuery.refetch()} />
        ) : pageOutOfRange ? (
          <EmptyState
            title="当前页已无数据"
            description={`商品数据有变化，正在为你返回第 ${lastPage} 页。`}
            action={
              <Button variant="secondary" size="sm" onClick={() => setPage(lastPage)}>
                回到第 {lastPage} 页
              </Button>
            }
          />
        ) : data && data.records.length > 0 ? (
          <>
            <div className="grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-3 xl:grid-cols-4">
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
