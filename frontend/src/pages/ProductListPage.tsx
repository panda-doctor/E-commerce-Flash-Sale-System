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
import { Countdown } from '../components/activity/Countdown'
import { formatPrice, stockPercent } from '../lib/format'
import { phaseOfItem } from '../lib/activityPhase'
import type { ActivityItem } from '../types/activity'

const PAGE_SIZE = 12

/** 首页/商品列表：真实分页 + 关键词搜索（300ms 防抖）+ 三态 + 进行中活动促销卡。 */
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

  const activeOriginal =
    activeActivity?.seckillPrice != null &&
    activeActivity.originalPrice != null &&
    activeActivity.seckillPrice < activeActivity.originalPrice

  return (
    <div className="pb-12">
      {/* 首屏：红橙渐变 Hero，品牌色真正参与布局，文字始终落在深红一侧保证对比度 */}
      <section className="container-page pt-5 sm:pt-6">
        <div className="relative overflow-hidden rounded-3xl bg-hero px-5 py-8 shadow-float sm:px-8 sm:py-10 lg:px-10 lg:py-12">
          <div
            aria-hidden
            className="pointer-events-none absolute -right-24 -top-28 size-80 rounded-full bg-brand-orange/40 blur-3xl"
          />
          <div
            aria-hidden
            className="pointer-events-none absolute -bottom-24 left-1/3 size-72 rounded-full bg-white/10 blur-2xl"
          />
          <div
            aria-hidden
            className="pointer-events-none absolute right-10 top-10 size-28 rounded-full border border-white/20"
          />

          <div className="relative grid gap-7 md:grid-cols-[1.02fr_0.98fr] md:items-center lg:grid-cols-[1.08fr_0.92fr] lg:gap-8">
            <div>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-white/30 bg-white/15 px-3 py-1 text-xs font-semibold text-white backdrop-blur-sm">
                <Flame className="size-3.5" aria-hidden />
                整点开抢 · 限时秒杀
              </span>
              <h1 className="mt-4 text-[30px] font-extrabold leading-[1.15] text-white sm:text-[40px] lg:text-[42px]">
                <span className="block">好物限时秒杀</span>
                <span className="mt-1 block">
                  抢到就是
                  <span className="text-[#FFD8C8]">赚到</span>
                </span>
              </h1>
              <p className="mt-3 max-w-lg text-sm leading-6 text-white sm:text-base">
                库存与价格均来自实时服务，下单成功即进入排队；一人一件，抢完即止。
              </p>
              <ul className="mt-5 flex flex-wrap gap-x-5 gap-y-2 text-xs text-white/90 sm:text-[13px]">
                <li className="flex items-center gap-1.5">
                  <ShieldCheck className="size-3.5 text-white/80" aria-hidden />
                  库存由服务端实时校验
                </li>
                <li className="flex items-center gap-1.5">
                  <Timer className="size-3.5 text-white/80" aria-hidden />
                  倒计时以服务端时间为准
                </li>
                <li className="flex items-center gap-1.5">
                  <ReceiptText className="size-3.5 text-white/80" aria-hidden />
                  排队结果随时可查
                </li>
              </ul>
            </div>

            {activeActivity ? (
              <Link
                to={`/activities/${activeActivity.activityId}`}
                className="group relative block rounded-2xl bg-white p-5 shadow-hero-card ring-1 ring-black/5 transition-transform duration-200 hover:-translate-y-0.5 focus-ring motion-reduce:transform-none sm:p-6"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-dark">
                    <span className="size-1.5 rounded-full bg-brand" aria-hidden />
                    正在进行
                  </span>
                  <span className="tnum text-xs text-ink-600">
                    已抢 {stockPercent(activeActivity.stock, activeActivity.totalStock)}%
                  </span>
                </div>
                <h2 className="mt-3 line-clamp-1 text-lg font-bold text-ink-900">
                  {activeActivity.activityName ?? '限时秒杀活动'}
                </h2>
                <p className="mt-1 line-clamp-1 text-xs text-ink-600">
                  {activeActivity.productName ?? '实时库存，先到先得'}
                </p>

                <div className="mt-4 flex flex-wrap items-baseline gap-x-2 gap-y-1">
                  <span className="text-[11px] font-medium text-brand-dark">秒杀价</span>
                  <span className="tnum text-3xl font-extrabold leading-none text-brand">
                    {formatPrice(activeActivity.seckillPrice)}
                  </span>
                  {activeOriginal ? (
                    <span className="tnum text-xs text-ink-600 line-through">
                      {formatPrice(activeActivity.originalPrice)}
                    </span>
                  ) : null}
                </div>

                <span className="mt-3 inline-flex items-center gap-1.5 rounded-full border border-brand/15 bg-brand-tint px-2.5 py-1">
                  <Countdown
                    target={activeActivity.endTime}
                    label="距结束"
                    onFinish={() => activitiesQuery.refetch()}
                  />
                </span>

                <span className="mt-5 flex h-11 items-center justify-center gap-1.5 rounded-xl bg-brand-solid text-sm font-semibold text-white shadow-sm transition-colors duration-150 group-hover:bg-brand-dark">
                  立即抢购
                  <ArrowRight className="size-4" aria-hidden />
                </span>
              </Link>
            ) : (
              <div className="rounded-2xl bg-white p-5 shadow-hero-card ring-1 ring-black/5 sm:p-6">
                <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-ink-600">
                  <Timer className="size-3.5 text-brand-orange" aria-hidden />
                  暂无进行中的秒杀
                </span>
                <p className="mt-3 text-sm leading-6 text-ink-900">
                  当前没有进行中的活动，先看看在售好物。
                </p>
                <p className="mt-1.5 text-xs leading-5 text-ink-600">
                  活动开始后，这里会展示实时秒杀价与倒计时。
                </p>
                <Link
                  to="/activities"
                  className="mt-4 inline-flex h-10 items-center gap-1.5 rounded-xl bg-brand-tint px-4 text-sm font-semibold text-brand-dark transition-colors duration-150 hover:bg-brand-soft focus-ring"
                >
                  浏览全部活动
                  <ArrowRight className="size-4" aria-hidden />
                </Link>
              </div>
            )}
          </div>
        </div>
      </section>

      <div className="container-page">
        {/* 工具栏：搜索、数量与刷新同一视觉容器 */}
        <div className="mt-7 flex flex-col gap-3 rounded-2xl border border-state-line bg-page-card p-3 shadow-card sm:flex-row sm:items-center">
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
              className="h-11 w-full rounded-xl border border-state-line bg-page-subtle/60 pl-10 pr-10 text-sm text-ink-900 outline-none transition-[border-color,box-shadow] duration-150 placeholder:text-ink-400 hover:border-state-hover focus:border-brand focus:bg-white focus:ring-2 focus:ring-brand/20"
            />
            {keyword ? (
              <button
                type="button"
                aria-label="清空搜索"
                onClick={() => onSearchChange('')}
                className="absolute right-2.5 top-1/2 flex size-7 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-ink-400 transition-colors duration-150 hover:bg-brand-tint hover:text-ink-900 focus-ring"
              >
                <X className="size-4" aria-hidden />
              </button>
            ) : null}
          </div>

          <div className="flex items-center justify-between gap-3 sm:ml-auto sm:justify-end">
            <span className="tnum text-sm text-ink-600">共 {total} 件商品</span>
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

        <div className="mb-5 mt-8 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="section-title">在售好物</h2>
            <p className="mt-1.5 text-sm text-ink-600">
              {debouncedKeyword ? `“${debouncedKeyword}” 的搜索结果` : '实时库存 · 售完即止'}
            </p>
          </div>
          <span className="chip border-brand/20 bg-brand-soft text-brand-dark">
            <Flame className="size-3.5" aria-hidden />
            秒杀进行中
          </span>
        </div>

        {productsQuery.isPending ? (
          <div className="grid grid-cols-2 gap-3.5 sm:gap-4 md:grid-cols-3 lg:gap-5 xl:grid-cols-4">
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
            <div className="grid grid-cols-2 gap-3.5 sm:gap-4 md:grid-cols-3 lg:gap-5 xl:grid-cols-4">
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
