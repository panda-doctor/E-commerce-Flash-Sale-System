<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/apiClient'
import type { ActivityItem, ActivityStatus, ProductDetail } from '../types/api'
import ProductImage from '../components/ProductImage.vue'

type ActivityFilter = 'ALL' | 'RUNNING' | 'NOT_STARTED' | 'ENDED' | 'SOLD_OUT'
type DisplayActivityStatus = ActivityStatus

const products = ref<ProductDetail[]>([])
const activities = ref<ActivityItem[]>([])
const loading = ref(true)
const refreshing = ref(false)
const loadError = ref('')
const refreshNotice = ref('')
const keyword = ref('')
const activeFilter = ref<ActivityFilter>('ALL')
const currentTime = ref(Date.now())
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)
let refreshTimer: ReturnType<typeof setInterval> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
let searchTimer: ReturnType<typeof setTimeout> | undefined
let catalogRequestSequence = 0
let focusHandler: (() => void) | undefined
const route = useRoute()
const router = useRouter()
const isFlashSalePage = computed(() => route.name === 'flash-sale')

const filters: { value: ActivityFilter; label: string }[] = [
  { value: 'ALL', label: '全部商品' },
  { value: 'RUNNING', label: '秒杀进行中' },
  { value: 'NOT_STARTED', label: '即将开始' },
  { value: 'SOLD_OUT', label: '已售罄' },
  { value: 'ENDED', label: '已结束' },
]

const activityByProduct = computed(() => {
  const priority: Record<DisplayActivityStatus, number> = {
    RUNNING: 0, NOT_STARTED: 1, SOLD_OUT: 2, ENDED: 3, CANCELLED: 4,
  }
  const result = new Map<string, ActivityItem>()
  for (const activity of activities.value) {
    const key = String(activity.productId)
    const current = result.get(key)
    if (!current || priority[displayStatus(activity)] < priority[displayStatus(current)]) {
      result.set(key, activity)
    }
  }
  return result
})

const visibleProducts = computed(() => products.value.filter((product) => {
  const activity = activityByProduct.value.get(String(product.productId))
  if (isFlashSalePage.value && !activity) return false
  if (activeFilter.value === 'ALL') return true
  if (!activity) return false
  const state = displayStatus(activity)
  if (activeFilter.value === 'ENDED') return state === 'ENDED' || state === 'CANCELLED'
  return state === activeFilter.value
}))

const productCards = computed(() => visibleProducts.value.map(product => ({
  product,
  activity: activityByProduct.value.get(String(product.productId)),
})))
const runningActivityCount = computed(() => activities.value.filter(item => displayStatus(item) === 'RUNNING').length)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const visiblePageNumbers = computed(() => {
  const count = Math.min(5, pageCount.value)
  const start = Math.max(1, Math.min(page.value - 2, pageCount.value - count + 1))
  return Array.from({ length: count }, (_, index) => start + index)
})
const featuredActivity = computed(() => activities.value
  .filter(item => ['RUNNING', 'NOT_STARTED'].includes(displayStatus(item)))
  .sort((a, b) => new Date(a.startTime.replace(' ', 'T')).getTime() - new Date(b.startTime.replace(' ', 'T')).getTime())[0])

onMounted(() => {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  void loadCatalog()
  refreshTimer = setInterval(() => { if (!document.hidden && !loading.value && !refreshing.value) void refreshCatalog() }, 20000)
  clockTimer = setInterval(() => {
    const before = activities.value.map(displayStatus).join('|')
    currentTime.value = Date.now()
    const after = activities.value.map(displayStatus).join('|')
    if (before !== after && !refreshing.value) void refreshCatalog()
  }, 1000)
  focusHandler = () => { if (!document.hidden && !loading.value && !refreshing.value) void refreshCatalog() }
  window.addEventListener('focus', focusHandler)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (clockTimer) clearInterval(clockTimer)
  if (searchTimer) clearTimeout(searchTimer)
  if (focusHandler) window.removeEventListener('focus', focusHandler)
})

watch(keyword, () => {
  page.value = 1
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    const query = { ...route.query }
    if (keyword.value.trim()) query.q = keyword.value.trim()
    else delete query.q
    void router.replace({ query })
    void loadCatalog()
  }, 300)
})

watch(() => route.query.q, (value) => {
  const query = typeof value === 'string' ? value : ''
  if (query !== keyword.value) keyword.value = query
})

async function loadCatalog() {
  const requestSequence = ++catalogRequestSequence
  loading.value = true
  loadError.value = ''
  try {
    const [activityItems, productPage] = await Promise.all([
      api.listActivities(),
      api.listProducts({ page: page.value, pageSize: pageSize.value, keyword: keyword.value.trim() || undefined }),
    ])
    if (requestSequence !== catalogRequestSequence) return
    activities.value = activityItems
    products.value = productPage.records
    total.value = productPage.total
  } catch (error) {
    if (requestSequence === catalogRequestSequence) {
      loadError.value = error instanceof Error ? error.message : '暂时无法读取商品，请稍后重试。'
    }
  } finally {
    if (requestSequence === catalogRequestSequence) loading.value = false
  }
}

async function refreshCatalog() {
  refreshing.value = true
  try {
    const [activityItems, productPage] = await Promise.all([
      api.listActivities(),
      api.listProducts({ page: page.value, pageSize: pageSize.value, keyword: keyword.value.trim() || undefined }),
    ])
    activities.value = activityItems
    products.value = productPage.records
    total.value = productPage.total
    refreshNotice.value = ''
  } catch {
    refreshNotice.value = '更新暂时失败，页面保留上次成功读取的数据。'
  } finally {
    refreshing.value = false
  }
}

function displayStatus(activity: ActivityItem): DisplayActivityStatus {
  if (activity.status === 'CANCELLED' || activity.status === 'SOLD_OUT') return activity.status
  const start = new Date(activity.startTime.replace(' ', 'T')).getTime()
  const end = new Date(activity.endTime.replace(' ', 'T')).getTime()
  if (!Number.isNaN(start) && !Number.isNaN(end)) {
    const now = currentTime.value
    if (now < start) return 'NOT_STARTED'
    if (now >= end) return 'ENDED'
    if (activity.stock <= 0) return 'SOLD_OUT'
    return 'RUNNING'
  }
  return activity.status
}

function statusLabel(activity?: ActivityItem): string {
  if (!activity) return '在售商品'
  const state = displayStatus(activity)
  return ({
    RUNNING: '秒杀进行中', NOT_STARTED: '即将开始', ENDED: '活动已结束',
    SOLD_OUT: '已售罄', CANCELLED: '活动已取消',
  } as Record<DisplayActivityStatus, string>)[state] ?? '活动状态'
}

function statusClass(activity?: ActivityItem): string {
  if (!activity) return 'on-sale'
  return displayStatus(activity).toLowerCase()
}

function activityTimeLabel(activity: ActivityItem): string {
  const state = displayStatus(activity)
  if (state === 'NOT_STARTED') return `${timeLabel(activity.startTime)} 开始`
  if (state === 'RUNNING') return `至 ${timeLabel(activity.endTime)}`
  return ''
}

function canJoin(activity?: ActivityItem): boolean {
  return activity != null && displayStatus(activity) === 'RUNNING'
}

function productLink(product: ProductDetail, activity?: ActivityItem): string {
  const path = `/products/${encodeURIComponent(product.productId)}`
  return activity ? `${path}?activity=${encodeURIComponent(activity.activityId)}` : path
}

function money(cents: number): string { return `¥${(cents / 100).toFixed(2)}` }

function timeLabel(value: string): string {
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  return `${String(date.getMonth() + 1).padStart(2, '0')}月${String(date.getDate()).padStart(2, '0')}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function countdown(activity: ActivityItem): string {
  const state = displayStatus(activity)
  if (state !== 'RUNNING' && state !== 'NOT_STARTED') return ''
  const target = new Date((state === 'RUNNING' ? activity.endTime : activity.startTime).replace(' ', 'T')).getTime()
  const seconds = Math.max(0, Math.floor((target - currentTime.value) / 1000))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  const clock = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
  return `${state === 'RUNNING' ? '距结束' : '距开始'} ${days ? `${days}天 ` : ''}${clock}`
}

function soldPercent(activity: ActivityItem): number {
  return activity.totalStock > 0
    ? Math.min(100, Math.max(0, Math.round((1 - activity.stock / activity.totalStock) * 100)))
    : 0
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > pageCount.value || nextPage === page.value) return
  page.value = nextPage
  void loadCatalog()
  document.querySelector('#catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="shop-home">
    <section class="shop-hero">
      <div class="shop-container shop-hero-inner">
        <div class="hero-copy">
          <p class="shop-eyebrow"><span class="eyebrow-dot"></span> 闪购集市 · {{ isFlashSalePage ? '限时秒杀' : '商品发现' }}</p>
          <h1>{{ isFlashSalePage ? '正在发生的秒杀，' : '发现喜欢的商品，' }}<br><em>{{ isFlashSalePage ? '活动信息一目了然。' : '真实商品与活动信息。' }}</em></h1>
          <p class="hero-description">商品、活动时间、价格与库存均来自商城服务。参与资格和订单结果由服务端确认。</p>
          <a class="hero-cta" href="#catalog">{{ isFlashSalePage ? '浏览秒杀商品' : '浏览全部商品' }}</a>
        </div>
        <div v-if="featuredActivity" class="catalog-overview feature-activity" aria-label="当前活动信息">
          <div class="feature-activity-head"><span class="feature-flash-icon">闪</span><span class="product-status" :class="displayStatus(featuredActivity).toLowerCase()"><i></i>{{ statusLabel(featuredActivity) }}</span></div>
          <p class="feature-activity-label">{{ featuredActivity.activityName }}</p>
          <strong class="feature-activity-name">{{ featuredActivity.productName }}</strong>
          <div class="feature-activity-bottom"><span class="feature-price">{{ money(featuredActivity.seckillPrice) }}</span><span class="feature-countdown">{{ countdown(featuredActivity) }}</span></div>
          <RouterLink class="feature-link" :to="`/products/${encodeURIComponent(featuredActivity.productId)}?activity=${encodeURIComponent(featuredActivity.activityId)}`">查看活动与商品</RouterLink>
        </div>
        <div v-else class="catalog-overview catalog-overview-empty" aria-label="商品与活动概况">
          <div class="overview-mark" aria-hidden="true">闪</div><div class="overview-divider"></div>
          <div class="overview-metric"><strong>{{ total }}</strong><span>件在售商品</span></div><div class="overview-metric"><strong>{{ runningActivityCount }}</strong><span>场进行中活动</span></div>
        </div>
      </div>
    </section>

    <section id="catalog" class="shop-container product-section">
      <div class="product-section-heading">
        <div><p class="shop-eyebrow">{{ isFlashSalePage ? '真实活动商品' : '当前上架' }}</p><h2>{{ isFlashSalePage ? '限时秒杀' : '全部商品' }}</h2></div>
        <button class="refresh-button" type="button" :disabled="loading || refreshing" @click="loadCatalog"><span :class="{ spinning: loading || refreshing }">↻</span> 刷新</button>
      </div>
      <div class="product-toolbar">
        <div class="filter-tabs" role="tablist" aria-label="按活动状态筛选商品">
          <button v-for="filter in filters" :key="filter.value" type="button" role="tab" :aria-selected="activeFilter === filter.value" :class="{ selected: activeFilter === filter.value }" @click="activeFilter = filter.value; page = 1; loadCatalog()">{{ filter.label }}<span v-if="filter.value === 'RUNNING' && runningActivityCount" class="tab-live"></span></button>
        </div>
        <label class="shop-search"><span aria-hidden="true">⌕</span><input v-model="keyword" aria-label="搜索商品名称" placeholder="搜索商品名称" @keydown.enter.prevent="loadCatalog"></label>
      </div>
      <p v-if="refreshNotice && !loadError" class="stock-refresh-notice" role="status">{{ refreshNotice }}</p>
      <div v-if="loadError" class="shop-state shop-error"><div class="state-icon">!</div><h3>商品暂时没有加载出来</h3><p>{{ loadError }}</p><button type="button" class="solid-button" @click="loadCatalog">重新加载</button></div>
      <div v-else-if="loading" class="product-grid" aria-label="商品加载中"><div v-for="index in 6" :key="index" class="product-skeleton"><div></div><span></span><i></i></div></div>
      <div v-else-if="productCards.length" class="product-grid">
        <article v-for="card in productCards" :key="card.product.productId" class="product-card">
          <RouterLink class="card-open" :to="productLink(card.product, card.activity)">
            <div class="product-visual">
              <ProductImage :src="card.product.imageUrl" :alt="card.product.name" variant="catalog" loading="lazy" />
              <span class="product-status" :class="statusClass(card.activity)"><i></i>{{ statusLabel(card.activity) }}</span>
            </div>
            <div class="product-info">
              <div v-if="card.activity" class="product-category"><span>{{ card.activity.activityName }}</span><time v-if="activityTimeLabel(card.activity)">{{ activityTimeLabel(card.activity) }}</time></div>
              <h3>{{ card.product.name }}</h3>
              <p class="product-description">{{ card.product.description || '商品介绍暂未填写。' }}</p>
              <div class="product-pricing">
                <template v-if="card.activity && ['RUNNING', 'NOT_STARTED'].includes(displayStatus(card.activity))">
                  <strong>{{ money(card.activity.seckillPrice) }}</strong><del>{{ money(card.product.originalPrice) }}</del>
                </template>
                <strong v-else>{{ money(card.product.originalPrice) }}</strong>
                <span v-if="card.activity && displayStatus(card.activity) === 'SOLD_OUT'" class="save-chip">已售罄</span>
              </div>
              <template v-if="card.activity">
                <div class="stock-row"><span>剩余库存 {{ card.activity.stock }} 件</span><span v-if="['RUNNING', 'NOT_STARTED'].includes(displayStatus(card.activity))">{{ countdown(card.activity) }}</span><span v-else>限购 {{ card.activity.limitPerUser }} 件</span></div>
                <div v-if="card.activity.totalStock > 0" class="stock-track"><span :style="{ width: `${soldPercent(card.activity)}%` }"></span></div>
              </template>
              <div v-else class="stock-row"><span>当前可售库存</span><span>{{ card.product.availableStock }} 件</span></div>
            </div>
          </RouterLink>
          <div class="product-card-foot"><RouterLink class="card-cta" :to="productLink(card.product, card.activity)">{{ canJoin(card.activity) ? '查看并参与' : '查看商品' }}</RouterLink></div>
        </article>
      </div>
      <div v-else class="shop-state"><div class="state-icon">⌕</div><h3>{{ keyword ? '没有找到相关商品' : activeFilter === 'ALL' ? '当前暂无在售商品' : '当前没有符合条件的商品' }}</h3><p>{{ keyword ? '试试更短的关键词，或清除筛选。' : '试试其他活动状态，或恢复全部商品。' }}</p><button v-if="keyword || activeFilter !== 'ALL'" type="button" class="text-button" @click="keyword = ''; activeFilter = 'ALL'">清除筛选</button></div>
      <nav v-if="!loading && !loadError && total > pageSize" class="catalog-pagination" aria-label="商品分页">
        <label>每页 <select v-model.number="pageSize" aria-label="每页商品数量" @change="page = 1; loadCatalog()"><option :value="12">12 件</option><option :value="24">24 件</option></select></label><button type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button><button v-for="number in visiblePageNumbers" :key="number" type="button" class="page-number" :aria-current="page === number ? 'page' : undefined" :class="{ selected: page === number }" @click="changePage(number)">{{ number }}</button><span>共 {{ total }} 件 · 第 {{ page }} / {{ pageCount }} 页</span><button type="button" :disabled="page >= pageCount" @click="changePage(page + 1)">下一页</button>
      </nav>
    </section>
  </div>
</template>
