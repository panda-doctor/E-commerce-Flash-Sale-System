<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { uiState, userStore } from '../utils/store'
import RankBoard from '../components/RankBoard.vue'
import MetricsPanel from '../components/MetricsPanel.vue'
import {
  fenToYuan,
  formatDuration,
  parseServerTime,
  CHECK_REASON_TEXT,
  ORDER_STATUS_TEXT,
} from '../utils/format'
import { toastErr, toastOK, toastWarn } from '../utils/toast'

/* 商品秒杀详情页（承接原"秒杀大厅"交互，重构为电商详情布局） */

const route = useRoute()
const router = useRouter()
const activityId = computed(() => String(route.params.id || ''))

const loading = ref(false)
const loadErr = ref('')
const activity = ref(null)
const product = ref(null)
const check = ref(null)

const busy = ref(false)
const pollState = ref('idle') // idle | polling | won | lost | duplicated
const orderNo = ref('')
const orderSince = ref('')
const hint = ref('')
const resultModal = ref('') // '' | 'won' | 'fail' —— 秒杀结果弹窗

const nowMs = ref(Date.now())
let clockTimer = null
let refreshTimer = null
let pollTimer = null
let pollTries = 0
let pollErrorNotified = false

/* ---------------- 派生 ---------------- */
const startMs = computed(() => parseServerTime(activity.value?.startTime))
const endMs = computed(() => parseServerTime(activity.value?.endTime))
const stock = computed(() =>
  activity.value?.stock != null ? Number(activity.value.stock) : null,
)

const phase = computed(() => {
  if (!activity.value) return 'loading'
  if (activity.value.status === 'CANCELLED') return 'cancelled'
  if (check.value?.reason === 'ACTIVITY_NOT_PREHEATED') return 'unpreheated'
  const now = nowMs.value
  if (stock.value === 0) return 'soldout'
  if (now < startMs.value) return 'not_started'
  if (now >= endMs.value) return 'ended'
  if (stock.value == null) return 'unpreheated'
  return 'running'
})

const canBuy = computed(
  () => phase.value === 'running' && !busy.value
    && !['polling', 'won', 'duplicated', 'lost'].includes(pollState.value),
)

const PHASE_META = {
  not_started: { text: '未开始', cls: 'pill-info-soft' },
  running: { text: '抢购中', cls: 'pill-hot' },
  unpreheated: { text: '未预热', cls: 'pill-soft' },
  soldout: { text: '已售罄', cls: 'pill-soft' },
  ended: { text: '已结束', cls: 'pill-soft' },
  cancelled: { text: '已取消', cls: 'pill-soft' },
}
const phaseMeta = computed(() => PHASE_META[phase.value] || PHASE_META.not_started)

const countdownLabel = computed(() => {
  if (phase.value === 'not_started') return '距开抢'
  if (phase.value === 'running' || phase.value === 'unpreheated') return '距结束'
  return ''
})
const countdownText = computed(() => {
  const now = nowMs.value
  if (!Number.isFinite(startMs.value) || !Number.isFinite(endMs.value)) return '--:--:--'
  if (phase.value === 'not_started') return formatDuration(startMs.value - now)
  if (phase.value === 'running' || phase.value === 'unpreheated')
    return formatDuration(endMs.value - now)
  return '--:--:--'
})

const buttonText = computed(() => {
  switch (phase.value) {
    case 'not_started': return '等待开抢'
    case 'unpreheated': return '库存未预热'
    case 'running':
      if (pollState.value === 'polling') return '排队建单中…'
      if (pollState.value === 'won') return '抢购成功'
      return '立即秒杀'
    case 'soldout': return '已抢光'
    case 'ended': return '活动已结束'
    case 'cancelled': return '活动已取消'
    default: return '立即秒杀'
  }
})
const btnDisabled = computed(
  () => busy.value || ['polling', 'won', 'duplicated', 'lost'].includes(pollState.value)
    || phase.value !== 'running',
)
const btnBusy = computed(() => busy.value || pollState.value === 'polling')

const discount = computed(() => {
  const o = Number(product.value?.originalPrice)
  const s = Number(activity.value?.seckillPrice)
  if (o && s && o > s) return Math.round(((o - s) / o) * 100)
  return 0
})

const checkNote = computed(() => {
  if (!check.value) return ''
  if (check.value.canJoin) return '可参与'
  return CHECK_REASON_TEXT[check.value.reason] || '当前不可参与'
})
const checkBad = computed(() => check.value && !check.value.canJoin)

const fallbackChar = computed(() => (activity.value?.activityName || '秒').slice(0, 1))

/* ---- 销量进度（方案：已抢 x% · 仅剩 x 件）----
   总量优先取详情 VO 的 totalStock（后端已补）；列表卡片跳转附带 ?total= 作旧链接兜底 */
const totalStock = computed(() => {
  const fromActivity = Number(activity.value?.totalStock)
  if (Number.isFinite(fromActivity) && fromActivity > 0) return fromActivity
  const q = Number(route.query.total)
  return Number.isFinite(q) && q > 0 ? q : null
})
const soldCount = computed(() =>
  totalStock.value && stock.value != null ? Math.max(0, totalStock.value - stock.value) : null,
)
const soldPct = computed(() => {
  if (!totalStock.value) return 0
  const remain = stock.value != null ? stock.value : totalStock.value
  return Math.max(0, Math.min(100, Math.round(((totalStock.value - remain) / totalStock.value) * 100)))
})
const urgent = computed(
  () => phase.value === 'running' && stock.value != null && stock.value < 10,
)
// 方案 4.1：最后 30 秒红色闪烁临界动效
const cdUrgent = computed(
  () => phase.value === 'running' && endMs.value - nowMs.value <= 30_000,
)

/* ---------------- 加载与轮询 ---------------- */

function resetSession() {
  stopOrderPoll()
  pollState.value = 'idle'
  orderNo.value = ''
  hint.value = ''
  activity.value = null
  product.value = null
  check.value = null
  loadErr.value = ''
}

async function loadActivity() {
  if (!activityId.value) return
  loading.value = true
  resetSession()
  try {
    const act = await api.getActivity(activityId.value)
    activity.value = act
    try {
      product.value = await api.getProduct(act.productId)
    } catch (e) {
      product.value = null
    }
    refreshMeta()
  } catch (e) {
    loadErr.value = e.code === 40004 ? '活动不存在或已被清理' : e.message
  } finally {
    loading.value = false
  }
}

async function refreshMeta() {
  if (!activityId.value || !activity.value) return
  try {
    activity.value = await api.getActivity(activityId.value)
  } catch (e) {
    /* 保留旧值 */
  }
  try {
    check.value = await api.checkActivity(activityId.value, userStore.userId)
  } catch (e) {
    check.value = null
  }
}

function stopOrderPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPoll(no) {
  orderNo.value = no
  pollState.value = 'polling'
  pollTries = 0
  pollErrorNotified = false
  orderSince.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  hint.value = '已进入排队队列，正在等待异步建单…'
  stopOrderPoll()
  pollTimer = setInterval(async () => {
    if (document.hidden) return
    pollTries += 1
    try {
      const od = await api.getOrder(orderNo.value)
      if (od && od.status === 'CREATED') {
        stopOrderPoll()
        pollState.value = 'won'
        hint.value = '订单创建成功，实时手速榜即将刷新'
        resultModal.value = 'won'
        toastOK(`抢购成功，订单 ${orderNo.value} 已创建`)
      } else if (pollTries >= 60) {
        stopOrderPoll()
        pollState.value = 'lost'
        hint.value = '订单创建超时，请到控制台核对'
        toastErr('排队超时：订单迟迟未创建')
      }
    } catch (e) {
      if (!pollErrorNotified) {
        pollErrorNotified = true
        hint.value = '订单状态查询失败，正在自动重试…'
        toastWarn('订单状态查询失败，正在自动重试')
      }
      if (pollTries >= 60) {
        stopOrderPoll()
        pollState.value = 'lost'
      }
    }
  }, 900)
}

async function onBuy() {
  if (!canBuy.value) return
  busy.value = true
  hint.value = '正在抢购…'
  try {
    const r = await api.execute(activityId.value, userStore.userId)
    if (r && r.orderNo) startPoll(r.orderNo)
  } catch (e) {
    if (e.code === 40901) {
      hint.value = '重复秒杀：尝试在榜单中同步你已入队的订单…'
      try {
        const list = await api.getRank(activityId.value, 100)
        const mine = (list || []).find((it) => it.userId === userStore.userId)
        if (mine && mine.orderNo) startPoll(mine.orderNo)
        else {
          pollState.value = 'duplicated'
          hint.value = '重复秒杀已被拦截（令牌已存在，你已参与本场）'
        }
      } catch (_) {
        pollState.value = 'duplicated'
        hint.value = '重复秒杀已被拦截（令牌已存在，你已参与本场）'
      }
    } else if (e.code === 40902) {
      hint.value = '库存不足，被别人抢先一步'
      resultModal.value = 'fail'
      toastErr('手慢了，库存已抢光')
    } else if (e.code === 42900) {
      hint.value = '请求过于频繁（60 秒窗口限 5 次）'
      toastWarn('触发限流，请稍候再试')
    } else if (e.code === 40004) {
      hint.value = '活动不存在或已被清理'
      toastErr(e.message)
    } else {
      hint.value = e.message || '请求失败'
      toastErr(hint.value)
    }
  } finally {
    busy.value = false
  }
}

/* ---- M10：我的抢购记录（interface 4.10，当前活动维度） ---- */
const myOrders = ref([])
const myOrdersErr = ref('')
async function loadMyOrders() {
  if (!activityId.value) return
  try {
    myOrders.value = await api.listMyOrders(userStore.userId, activityId.value)
    myOrdersErr.value = ''
  } catch (e) {
    // 无有效令牌（40100）等场景静默置空，用一行提示说明原因
    myOrders.value = []
    myOrdersErr.value = e.code === 40100 ? '当前用户无有效访问令牌，可到顶栏领取' : '（加载失败，可点击刷新）'
  }
}

/* ---- 更多秒杀场次推荐（底部横向滚动，排除当前活动）---- */
const moreList = ref([])
async function loadMore() {
  try {
    const data = await api.listActivities()
    const all = Array.isArray(data) ? data : []
    moreList.value = all
      .filter((it) => String(it.activityId) !== activityId.value)
      .slice(0, 10)
  } catch (e) {
    moreList.value = []
  }
}
function hmOf(s) {
  const d = new Date(parseServerTime(s))
  if (Number.isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}
function morePhase(it) {
  const now = nowMs.value
  const start = parseServerTime(it.startTime)
  const end = parseServerTime(it.endTime)
  const stk = it.stock != null ? Number(it.stock) : null
  if (it.status === 'CANCELLED') return 'over'
  if (now < start) return 'soon'
  if (now >= end) return 'over'
  if (stk === 0) return 'over'
  if (stk == null) return 'soon'
  return 'live'
}
function openMore(it) {
  router.push(`/activity/${it.activityId}`)
}

watch(
  () => activityId.value,
  (v) => {
    if (v) {
      uiState.setActivity(v)
      loadActivity()
      loadMore()
      loadMyOrders()
    }
  },
)

onMounted(() => {
  clockTimer = setInterval(() => (nowMs.value = Date.now()), 250)
  refreshTimer = setInterval(() => {
    if (!document.hidden) refreshMeta()
  }, 2600)
  if (activityId.value) loadActivity()
  loadMore()
  loadMyOrders()
})
onBeforeUnmount(() => {
  clearInterval(clockTimer)
  clearInterval(refreshTimer)
  stopOrderPoll()
})
</script>

<template>
  <div class="dwrap container">
    <nav class="crumbs">
      <RouterLink to="/" class="back">
        <span class="back-arrow">←</span>
        <span>返回活动广场</span>
      </RouterLink>
      <span class="crumb-sep" v-if="activity">/</span>
      <span v-if="activity" class="crumb-cur">{{ activity.activityName }}</span>
    </nav>

    <!-- 错误 / 空态 -->
    <div v-if="loadErr" class="panel err-box">
      <div class="err-ico">⚠</div>
      <h3>{{ loadErr }}</h3>
      <RouterLink class="btn btn-cta btn-sm" to="/">返回广场</RouterLink>
    </div>

    <div v-else-if="!activity" class="panel err-box">
      <div class="skeleton line" style="height: 40px; width: 55%"></div>
      <div class="skeleton line" style="height: 320px; margin-top: 14px"></div>
    </div>

    <template v-else>
      <!-- 主体：媒体 + 购买 -->
      <section class="detail">
        <div class="media panel">
          <div class="media-stage" :class="{ hot: phase === 'running' }">
            <img v-if="product && product.imageUrl" :src="product.imageUrl" :alt="product.name" />
            <div v-else class="media-fallback">{{ fallbackChar }}</div>
            <div class="media-shine"></div>
            <span v-if="discount" class="save-tag">秒杀省 {{ discount }}%</span>
            <span v-if="phase === 'running'" class="live-flag"><i></i>LIVE</span>
          </div>
          <div class="media-info">
            <p v-if="product" class="desc">{{ product.description || product.name }}</p>
            <div class="assure-title">技术保障</div>
            <ul class="assure">
              <li><span class="a-dot"></span>Lua 原子扣减</li>
              <li><span class="a-dot"></span>消息队列削峰</li>
              <li><span class="a-dot"></span>DB 唯一键兜底</li>
              <li><span class="a-dot"></span>异步建单</li>
            </ul>
          </div>
        </div>

        <aside class="buy panel">
          <div class="buy-head">
            <span class="pill" :class="phaseMeta.cls">{{ phaseMeta.text }}</span>
            <span class="aid hnum">#{{ activity.activityId }}</span>
          </div>

          <h2 class="name">{{ activity.activityName }}</h2>
          <p class="pname" v-if="product">{{ product.name }}</p>

          <div class="price-block">
            <div class="price-main">
              <span class="yen">¥</span>
              <span class="big hnum">{{ fenToYuan(activity.seckillPrice) }}</span>
            </div>
            <div class="price-aux">
              <span class="orig hnum" v-if="product">¥{{ fenToYuan(product.originalPrice) }}</span>
              <span v-if="product && product.originalPrice > activity.seckillPrice" class="save hnum">
                省 ¥{{ fenToYuan(product.originalPrice - activity.seckillPrice) }}
              </span>
            </div>
          </div>

          <dl class="meta">
            <div><dt>开抢时间</dt><dd class="hnum">{{ activity.startTime }}</dd></div>
            <div><dt>结束时间</dt><dd class="hnum">{{ activity.endTime }}</dd></div>
            <div><dt>限购</dt><dd>{{ activity.limitPerUser }} 件 / 人</dd></div>
          </dl>

          <div class="cd-wrap" v-if="countdownLabel">
            <span class="cd-label">{{ countdownLabel }}</span>
            <span class="cd-num hnum" :class="{ ur: cdUrgent }">{{ countdownText }}</span>
          </div>

          <!-- 销量进度（方案 3.2）：已抢 x% · 仅剩 x 件 -->
          <div v-if="totalStock" class="sell-row">
            <div class="sell-head">
              <span>
                已抢 <b class="hnum">{{ soldCount }}</b> 件
                <em v-if="urgent" class="rush-tag">仅剩 {{ stock }} 件 · 即将售罄</em>
                <em v-else>仅剩 {{ stock }} 件</em>
              </span>
              <span class="pct hnum">{{ soldPct }}%</span>
            </div>
            <div class="bar" :class="{ ur: urgent || soldPct >= 90 }">
              <i :style="{ width: soldPct + '%' }"></i>
            </div>
          </div>
          <div v-else class="stock-note">
            实时库存 {{ stock == null ? '未预热（请先预热）' : stock + ' 件' }}
          </div>

          <button
            class="btn btn-cta buy-btn"
            :class="{ 'is-busy': btnBusy, 'is-ok': pollState === 'won' }"
            :disabled="btnDisabled"
            @click="onBuy"
          >
            <span v-if="btnBusy" class="spin"></span>
            {{ buttonText }}
          </button>

          <p v-if="hint" class="hint" :class="pollState === 'won' ? 'ok' : ''">{{ hint }}</p>
          <p v-if="checkNote" class="note" :class="{ bad: checkBad }">
            {{ checkBad ? '当前状态：' + checkNote : '已就绪：' + checkNote }}
          </p>

          <div v-if="phase === 'unpreheated'" class="preheat">
            库存键未预热，本场暂不可抢购。预热请到
            <RouterLink class="link" to="/admin">管理控制台</RouterLink>
          </div>

          <!-- 订单轮询结果 -->
          <Transition name="fade-slide">
            <div v-if="pollState !== 'idle'" class="result" :class="pollState">
              <span class="dot"></span>
              <div>
                <b>{{ pollState === 'polling' ? '排队中' : pollState === 'won' ? '抢购成功' : pollState === 'lost' ? '处理超时' : '已参与' }}</b>
                <span v-if="orderNo" class="ono hnum">{{ orderNo }}</span>
                <span v-else-if="orderSince" class="ono hnum">发起于 {{ orderSince }}</span>
              </div>
            </div>
          </Transition>
        </aside>
      </section>

      <!-- 我的抢购记录（interface 4.10） -->
      <section class="panel my-order">
        <header class="my-order-head">
          <div class="my-order-title">
            <span class="my-order-bar"></span>
            <h3>我的抢购记录</h3>
            <span class="my-order-sub">当前用户 #{{ userStore.userId }}</span>
          </div>
          <button class="my-order-refresh" @click="loadMyOrders">刷新</button>
        </header>
        <ul v-if="myOrders.length" class="my-order-list">
          <li v-for="od in myOrders" :key="od.orderNo" class="my-order-row">
            <span class="mo-no hnum">{{ od.orderNo }}</span>
            <span class="mo-status">{{ ORDER_STATUS_TEXT[od.status] || od.status }}</span>
            <time class="mo-time hnum">{{ od.createdAt }}</time>
          </li>
        </ul>
        <p v-else class="my-order-empty">
          {{ myOrdersErr || '本场你还没有秒杀成功记录，先去抢一单吧' }}
        </p>
      </section>

      <!-- 榜单与指标 -->
      <section class="below">
        <div class="rank-panel"><RankBoard :activity-id="activity.activityId" /></div>
        <div class="metric-panel"><MetricsPanel :activity-id="activity.activityId" /></div>
      </section>

      <!-- 更多秒杀场次推荐 -->
      <section class="more-sec" v-if="moreList.length">
        <header class="more-head">
          <div class="more-title">
            <span class="more-bar"></span>
            <h3>更多秒杀场次</h3>
          </div>
          <RouterLink to="/" class="more-link">查看全部 →</RouterLink>
        </header>
        <div class="more-rail">
          <button
            v-for="it in moreList"
            :key="it.activityId"
            class="mcard"
            :class="morePhase(it)"
            @click="openMore(it)"
          >
            <div class="mc-thumb">
              <img v-if="it.productImage" :src="it.productImage" :alt="it.productName" loading="lazy" />
              <div v-else class="mc-fallback">{{ (it.activityName || '秒').slice(0, 1) }}</div>
              <span class="mc-badge">{{ { live: '抢购中', soon: '预告', over: '已结束' }[morePhase(it)] }}</span>
            </div>
            <div class="mc-body">
              <p class="mc-name">{{ it.productName || it.activityName }}</p>
              <div class="mc-price">
                <span class="mc-yen">¥</span><b class="hnum">{{ fenToYuan(it.seckillPrice) }}</b>
              </div>
              <span class="mc-time hnum">{{ hmOf(it.startTime) }} 开抢</span>
            </div>
          </button>
        </div>
      </section>
    </template>

    <!-- 秒杀结果弹窗（方案 3.3） -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="resultModal" class="modal-mask" @click.self="resultModal = ''">
          <div class="modal-box" role="dialog" aria-modal="true">
            <div class="m-ico" :class="resultModal">
              <span v-if="resultModal === 'won'">✓</span>
              <span v-else>✕</span>
            </div>
            <h3>{{ resultModal === 'won' ? '秒杀成功' : '手慢了，已抢光' }}</h3>
            <p v-if="resultModal === 'won'" class="ono hnum">订单号：{{ orderNo || '—' }}</p>
            <p v-else>本场库存已被抢光，去看看其它场次吧</p>
            <div class="m-actions">
              <button
                v-if="resultModal === 'won'"
                class="btn btn-outline btn-sm"
                @click="resultModal = ''"
              >
                查看榜单
              </button>
              <button class="btn btn-cta btn-sm" @click="router.push('/')">返回会场</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 移动端吸底购买条 -->
    <Transition name="fade-slide">
      <div v-if="activity" class="buybar">
        <button class="bb-home" aria-label="返回会场" @click="router.push('/')">‹</button>
        <div class="bb-price">
          <span class="bb-yen">¥</span>
          <span class="bb-num hnum">{{ fenToYuan(activity.seckillPrice) }}</span>
        </div>
        <button class="btn btn-cta bb-btn" :disabled="btnDisabled" @click="onBuy">
          <span v-if="btnBusy" class="spin"></span>
          {{ buttonText }}
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.dwrap {
  padding: 22px 28px 80px;
}
.crumbs {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--text-2);
  margin-bottom: 20px;
}
.back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text);
  font-weight: 600;
  padding: 6px 12px;
  border-radius: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  transition: all 0.16s var(--ease-out);
}
.back:hover {
  color: var(--accent);
  border-color: rgba(255, 45, 85, 0.3);
  background: var(--accent-soft);
}
.back-arrow {
  transition: transform 0.16s;
}
.back:hover .back-arrow {
  transform: translateX(-2px);
}
.crumb-sep {
  color: var(--text-3);
}
.crumb-cur {
  color: var(--text-2);
  font-weight: 600;
}

.err-box {
  padding: 64px 28px;
  text-align: center;
}
.err-ico {
  font-size: 44px;
  margin-bottom: 12px;
}
.err-box h3 {
  margin: 0 0 18px;
}

.detail {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(340px, 0.75fr);
  gap: 24px;
  align-items: start;
}

.media {
  overflow: hidden;
}
.media-stage {
  position: relative;
  height: 380px;
  background: linear-gradient(140deg, #fef2ea, #ffe8d6 60%, #ffe4ec);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.media-stage.hot {
  background: linear-gradient(140deg, #fff3ea, #ffe6d2 55%, #ffd9c4);
}
.media-stage img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.media-shine {
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent 30%, rgba(255, 255, 255, 0.2) 50%, transparent 70%);
  pointer-events: none;
}
.media-fallback {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  font-size: 76px;
  font-weight: 900;
  color: #fff;
  background: linear-gradient(135deg, #ff5e3a, #ff2d55);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 22px 44px -14px rgba(255, 45, 85, 0.5);
}
.save-tag {
  position: absolute;
  left: 16px;
  bottom: 14px;
  color: #fff;
  background: var(--grad-cta);
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
  box-shadow: 0 4px 12px rgba(255, 45, 85, 0.35);
}
.live-flag {
  position: absolute;
  right: 14px;
  top: 14px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(220, 38, 38, 0.92);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1px;
  padding: 4px 10px;
  border-radius: 6px;
  backdrop-filter: blur(4px);
}
.live-flag i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: blink 1.2s ease infinite;
}
@keyframes blink {
  50% { opacity: 0.25; }
}
.media-info {
  padding: 16px 20px 18px;
}
.media-info .desc {
  margin: 0 0 14px;
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.65;
}
.assure-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-3);
  letter-spacing: 1px;
  margin-bottom: 8px;
}
.assure {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.assure li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4b5160;
  background: var(--surface-2);
  border: 1px solid var(--border);
  padding: 5px 11px;
  border-radius: 8px;
  font-weight: 600;
}
.a-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--grad-cta);
}

.buy {
  position: sticky;
  top: 84px;
  padding: 24px 26px 22px;
  display: flex;
  flex-direction: column;
  gap: 13px;
}
.buy-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.aid {
  font-size: 13px;
  color: var(--text-3);
  font-family: var(--font-num);
}
.name {
  margin: 0;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.3;
}
.pname {
  margin: 0;
  color: var(--text-2);
  font-size: 14px;
}
.price-block {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  background: linear-gradient(135deg, #fff7f4, #fff0ed);
  border: 1px solid #ffe0d3;
  border-radius: 14px;
  padding: 14px 18px;
}
.price-main {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.yen {
  color: var(--cta);
  font-weight: 800;
  font-size: 18px;
}
.big {
  color: var(--cta);
  font-size: 40px;
  font-weight: 900;
  font-family: var(--font-num);
  letter-spacing: -1px;
  line-height: 1;
}
.price-aux {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.orig {
  color: var(--text-3);
  text-decoration: line-through;
  font-size: 15px;
}
.save {
  font-size: 12px;
  font-weight: 800;
  color: #fff;
  background: var(--grad-cta);
  padding: 3px 10px;
  border-radius: 999px;
}

.meta {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: var(--surface-2);
  border-radius: 12px;
}
.meta > div {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.meta dt {
  color: var(--text-2);
}
.meta dd {
  margin: 0;
  font-weight: 600;
}

.cd-wrap {
  display: flex;
  align-items: baseline;
  gap: 12px;
  border: 1px dashed var(--border-strong);
  border-radius: 12px;
  padding: 13px 16px;
  background: var(--surface);
}
.cd-label {
  font-size: 14px;
  color: var(--text-2);
  font-weight: 600;
}
.cd-num {
  font-size: 28px;
  font-weight: 900;
  font-family: var(--font-num);
  letter-spacing: 2px;
  color: var(--accent);
}

.buy-btn {
  width: 100%;
  height: 56px;
  font-size: 20px;
  letter-spacing: 6px;
  border-radius: 14px;
  margin-top: 2px;
}
.buy-btn.is-busy {
  background: var(--cta-hover);
}
.buy-btn.is-ok {
  background: #34c759;
  box-shadow: 0 6px 18px -6px rgba(52, 199, 89, 0.5);
}
.buy-btn .spin {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  animation: rot 0.7s linear infinite;
}
@keyframes rot {
  to { transform: rotate(360deg); }
}

.hint {
  margin: 0;
  font-size: 14px;
  color: var(--text-2);
}
.hint.ok {
  color: #34c759;
  font-weight: 700;
}
.note {
  margin: 0;
  font-size: 13px;
  color: #34c759;
}
.note.bad {
  color: var(--warning);
}
.preheat {
  font-size: 13px;
  color: #92600a;
  background: var(--warning-soft);
  border-radius: 10px;
  padding: 11px 13px;
}
.link {
  color: var(--warning);
  font-weight: 800;
  text-decoration: underline;
}

.result {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 12px;
  padding: 12px 14px;
  font-size: 13px;
}
.result .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: none;
}
.result > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.result.polling {
  background: var(--info-soft);
  color: var(--info);
}
.result.polling .dot {
  background: var(--info);
  animation: blink 1.1s ease infinite;
}
.result.won {
  background: rgba(52, 199, 89, 0.1);
  color: #2a9e48;
}
.result.won .dot {
  background: #34c759;
}
.result.lost,
.result.duplicated {
  background: var(--surface-2);
  color: #555;
}
.result.lost .dot {
  background: var(--text-3);
}
.result.duplicated .dot {
  background: var(--text-3);
}
.ono {
  font-family: var(--font-num);
  font-size: 12px;
  opacity: 0.8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 我的抢购记录（M10） ---------- */
.my-order {
  margin-top: 26px;
  padding: 18px 22px 12px;
}
.my-order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.my-order-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.my-order-bar {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: var(--grad-cta);
}
.my-order-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
}
.my-order-sub {
  font-size: 12px;
  color: var(--text-3);
  font-weight: 600;
}
.my-order-refresh {
  border: 1px solid var(--border-strong);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 700;
  padding: 5px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.my-order-refresh:hover {
  color: var(--accent);
  border-color: var(--accent);
}
.my-order-list {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
}
.my-order-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px dashed var(--border);
  font-size: 13px;
}
.my-order-row:last-child {
  border-bottom: none;
}
.mo-no {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
  font-weight: 600;
}
.mo-status {
  color: var(--success);
  font-weight: 700;
  flex: none;
}
.mo-time {
  color: var(--text-3);
  font-size: 12px;
  flex: none;
}
.my-order-empty {
  color: var(--text-3);
  font-size: 13px;
  text-align: center;
  padding: 18px 0 8px;
}

.below {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
  gap: 24px;
  margin-top: 26px;
  align-items: start;
}

/* ---------- 更多秒杀场次 ---------- */
.more-sec {
  margin-top: 30px;
}
.more-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.more-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.more-bar {
  width: 4px;
  height: 20px;
  background: var(--grad-cta);
  border-radius: 2px;
}
.more-title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
}
.more-link {
  font-size: 14px;
  font-weight: 700;
  color: var(--accent);
  transition: opacity 0.15s;
}
.more-link:hover {
  opacity: 0.7;
}
.more-rail {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 14px;
}
.mcard {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
  text-align: left;
  transition: transform 0.18s var(--ease-out), box-shadow 0.18s var(--ease-out),
    border-color 0.18s;
}
.mcard:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-2);
  border-color: rgba(255, 45, 85, 0.2);
}
.mc-thumb {
  position: relative;
  aspect-ratio: 4 / 3;
  background: linear-gradient(140deg, #fff0ef, #ffe3de);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.mc-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mcard.over .mc-thumb {
  filter: grayscale(0.7);
  opacity: 0.72;
}
.mc-fallback {
  font-size: 36px;
  font-weight: 900;
  color: #fff;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff5e3a, #ff2d55);
  display: flex;
  align-items: center;
  justify-content: center;
}
.mc-badge {
  position: absolute;
  left: 8px;
  top: 8px;
  font-size: 11px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(120deg, #ff2d55, #ff5e3a);
  padding: 2px 9px;
  border-radius: 999px;
}
.mcard.soon .mc-badge {
  background: var(--aux);
}
.mcard.over .mc-badge {
  background: var(--text-3);
}
.mc-body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.mc-name {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.mc-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.mc-yen {
  color: var(--accent);
  font-weight: 800;
  font-size: 13px;
}
.mc-price b {
  color: var(--accent);
  font-size: 20px;
  font-weight: 900;
  font-family: var(--font-num);
}
.mc-time {
  font-size: 12px;
  color: var(--text-3);
  font-weight: 600;
}

/* 移动端吸底购买条 */
.buybar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: none;
  align-items: center;
  gap: 14px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.97);
  backdrop-filter: blur(10px);
  border-top: 1px solid var(--border);
  box-shadow: 0 -6px 24px rgba(0, 0, 0, 0.1);
}
.bb-price {
  display: flex;
  align-items: baseline;
}
.bb-yen {
  color: var(--cta);
  font-weight: 800;
}
.bb-num {
  color: var(--cta);
  font-size: 24px;
  font-weight: 900;
  font-family: var(--font-num);
}
.bb-btn {
  flex: 1;
  height: 46px;
  font-size: 17px;
  letter-spacing: 3px;
}
.bb-btn .spin {
  width: 16px;
  height: 16px;
  border: 3px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: rot 0.7s linear infinite;
}

/* ---------- 销量进度（方案 3.2） ---------- */
.sell-row {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.sell-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--text-2);
}
.sell-head b {
  color: #ff2d55;
  font-family: var(--font-num);
}
.rush-tag {
  font-style: normal;
  margin-left: 6px;
  color: #ff2d55;
  background: #ffe3e0;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  animation: rushPulse2 1.1s ease-in-out infinite;
}
@keyframes rushPulse2 {
  50% { transform: scale(1.05); background: #ffd5d0; }
}
.sell-row .pct {
  font-weight: 800;
  color: #ff2d55;
  font-family: var(--font-num);
}
.sell-row .bar {
  height: 8px;
  border-radius: 999px;
  background: var(--surface-3);
  overflow: hidden;
}
.sell-row .bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #ff9500, #ff2d55);
  transition: width 0.45s var(--ease-out);
}
.bar.ur i {
  animation: soldFlash 0.9s ease infinite;
}
@keyframes soldFlash {
  50% { opacity: 0.4; }
}
.stock-note {
  font-size: 13px;
  color: var(--text-2);
  background: var(--surface-2);
  border-radius: 10px;
  padding: 10px 13px;
}
.cd-num.ur {
  color: #ff2d55;
  animation: cdUr 1s ease infinite;
}
@keyframes cdUr {
  50% { opacity: 0.35; }
}

.bb-home {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--surface-2);
  border: 1px solid var(--border);
  color: var(--text);
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

/* ---------- 秒杀结果弹窗（方案 3.3） ---------- */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 80;
  padding: 24px;
}
.modal-box {
  width: 340px;
  max-width: 100%;
  background: #fff;
  border-radius: 20px;
  padding: 30px 24px 22px;
  text-align: center;
  box-shadow: var(--shadow-3);
}
.m-ico {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: popIn 0.4s var(--ease-spring);
}
.m-ico.won {
  background: linear-gradient(135deg, #34c759, #2a9e48);
  box-shadow: 0 10px 28px -6px rgba(52, 199, 89, 0.55);
}
.m-ico.fail {
  background: linear-gradient(135deg, #9ca3af, #6b7280);
}
.m-ico span {
  color: #fff;
  font-size: 32px;
  font-weight: 900;
}
@keyframes popIn {
  0% { transform: scale(0.3); opacity: 0; }
  100% { transform: scale(1); opacity: 1; }
}
.modal-box h3 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 900;
}
.modal-box p {
  margin: 0;
  font-size: 14px;
  color: var(--text-2);
}
.modal-box .ono {
  font-family: var(--font-num);
  color: var(--text);
  font-weight: 600;
  word-break: break-all;
}
.m-actions {
  margin-top: 22px;
  display: flex;
  gap: 10px;
  justify-content: center;
}
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.22s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-active .modal-box,
.modal-leave-active .modal-box {
  transition: transform 0.24s var(--ease-spring), opacity 0.2s ease;
}
.modal-enter-from .modal-box,
.modal-leave-to .modal-box {
  transform: scale(0.92) translateY(12px);
  opacity: 0;
}

@media (max-width: 980px) {
  .detail {
    grid-template-columns: 1fr;
  }
  .buy {
    position: static;
  }
  .below {
    grid-template-columns: 1fr;
  }
  .buybar {
    display: flex;
  }
  .dwrap {
    padding-bottom: 120px;
  }
}
@media (max-width: 640px) {
  .dwrap {
    padding-left: 16px;
    padding-right: 16px;
  }
  .media-stage {
    height: 240px;
  }
  .name {
    font-size: 20px;
  }
  .big {
    font-size: 32px;
  }
}
</style>
