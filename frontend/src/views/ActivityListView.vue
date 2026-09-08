<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import ActivityCard from '../components/ActivityCard.vue'
import { formatDuration, parseServerTime } from '../utils/format'
import { toastOK } from '../utils/toast'

/* ================================================================
   秒杀主会场（首页）
   信息架构：秒杀 Banner + 全场倒计时 → 场次时间轴 → 正在秒杀 →
            即将开抢（可预约）→ 已抢光回顾；顶部搜索作用于全部场次
   ================================================================ */
const router = useRouter()
const activities = ref([])
const loading = ref(true)
const keyword = ref('')
const nowMs = ref(Date.now())
const filter = ref('all') // all | live | soon | over

const filterTabs = [
  { key: 'all', label: '全部' },
  { key: 'live', label: '抢购中' },
  { key: 'soon', label: '即将开抢' },
  { key: 'over', label: '已结束' },
]
const showSec = (key) => filter.value === 'all' || filter.value === key

function phaseOf(it) {
  if (it.status === 'CANCELLED') return 'over'
  const now = nowMs.value
  const start = parseServerTime(it.startTime)
  const end = parseServerTime(it.endTime)
  const stock = it.stock != null ? Number(it.stock) : null
  if (now < start) return 'soon'
  if (now >= end) return 'over'
  if (stock === 0) return 'over'
  if (stock == null) return 'soon' // 未预热
  return 'live'
}

const byKeyword = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return activities.value
  return activities.value.filter((it) =>
    `${it.activityName ?? ''} ${it.productName ?? ''}`.toLowerCase().includes(kw),
  )
})
const liveList = computed(() => byKeyword.value.filter((a) => phaseOf(a) === 'live'))
const soonList = computed(() => byKeyword.value.filter((a) => phaseOf(a) === 'soon'))
const overList = computed(() => byKeyword.value.filter((a) => phaseOf(a) === 'over'))

// 全场倒计时：优先进行中场次（距结束）；否则最近一场预告（距开抢）
const heroActivity = computed(() => {
  const sorted = [...activities.value].sort(
    (a, b) => parseServerTime(a.startTime) - parseServerTime(b.startTime),
  )
  return sorted.find((a) => phaseOf(a) === 'live')
    || [...sorted].reverse().find((a) => phaseOf(a) === 'soon')
    || null
})
const heroPhase = computed(() => (heroActivity.value ? phaseOf(heroActivity.value) : null))
const heroCd = computed(() => {
  if (!heroActivity.value) return { label: '', text: '--:--:--' }
  const end = parseServerTime(heroActivity.value.endTime)
  const start = parseServerTime(heroActivity.value.startTime)
  if (heroPhase.value === 'live') {
    return { label: '本场距结束', text: formatDuration(end - nowMs.value) }
  }
  if (heroPhase.value === 'soon') {
    return { label: '下一场开抢', text: formatDuration(start - nowMs.value) }
  }
  return { label: '', text: '--:--:--' }
})

// 场次时间轴：按开始时刻排列的横向导航
const timeline = computed(() =>
  [...byKeyword.value].sort(
    (a, b) => parseServerTime(a.startTime) - parseServerTime(b.startTime),
  ),
)
function hmOf(it) {
  const d = new Date(parseServerTime(it.startTime))
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}
const tlPhaseClass = (it) => ({ live: 'tl-live', soon: 'tl-soon', over: 'tl-over' }[phaseOf(it)])
function openIt(it) {
  router.push(`/activity/${it.activityId}`)
}

/* ---- 预约到点提醒：轮询扫描 localStorage 的预约项，开抢瞬间 toast ---- */
const notifiedReminds = new Set()
function scanReminds() {
  if (document.hidden) return
  const all = activities.value
  for (const it of all) {
    const key = `fs_remind_${it.activityId}`
    if (localStorage.getItem(key) !== '1') continue
    const start = parseServerTime(it.startTime)
    if (start > 0 && nowMs.value >= start && !notifiedReminds.has(it.activityId)) {
      notifiedReminds.add(it.activityId)
      localStorage.removeItem(key)
      localStorage.removeItem(key + '_start')
      toastOK(`「${it.activityName}」已开抢，快去抢购！`)
    }
  }
}

async function load() {
  try {
    const data = await api.listActivities()
    activities.value = Array.isArray(data) ? data : []
  } catch (e) {
    activities.value = []
  } finally {
    loading.value = false
  }
}

let clockTimer = null
let refreshTimer = null
onMounted(() => {
  load()
  clockTimer = setInterval(() => {
    nowMs.value = Date.now()
    scanReminds()
  }, 600)
  refreshTimer = setInterval(() => {
    if (!document.hidden) load()
  }, 8000)
})
onBeforeUnmount(() => {
  clearInterval(clockTimer)
  clearInterval(refreshTimer)
})
</script>

<template>
  <div class="mall">
    <!-- 秒杀氛围 Banner + 全场倒计时 -->
    <section class="banner">
      <div class="banner-glow"></div>
      <div class="banner-grid"></div>
      <div class="banner-inner container">
        <div class="b-left">
          <p class="eyebrow">⚡ FLASH SALE</p>
          <h1>每日秒杀<span class="hl">整点开抢</span></h1>
          <p class="sub">Lua 原子防超卖 · 消息削峰异步建单 · 实时手速榜 · 每人限购 1 件</p>
          <div class="b-stats">
            <div class="stat"><b class="hnum">{{ liveList.length }}</b><span>抢购中</span></div>
            <div class="stat-sep"></div>
            <div class="stat"><b class="hnum">{{ soonList.length }}</b><span>即将开抢</span></div>
            <div class="stat-sep"></div>
            <div class="stat"><b class="hnum">{{ activities.length }}</b><span>总场次</span></div>
          </div>
          <RouterLink class="cta-link" to="/admin">
            <span>我是商家 · 创建场次</span>
            <span class="arrow">→</span>
          </RouterLink>
        </div>

        <div class="b-right" v-if="heroActivity">
          <div class="cd-head">
            <span class="cd-dot" :class="heroPhase"></span>
            <p class="cd-label">{{ heroCd.label }}</p>
          </div>
          <div class="cd-block hnum">
            <template v-for="(ch, i) in heroCd.text.split('')" :key="i">
              <span v-if="ch === ':'" class="cd-colon">:</span>
              <span v-else class="cd-cell">{{ ch }}</span>
            </template>
          </div>
          <p class="cd-name">{{ heroActivity.activityName }}</p>
          <button class="go-btn" @click="openIt(heroActivity)">
            {{ heroPhase === 'live' ? '进入本场抢购' : '查看预告' }}
            <span class="go-arrow">→</span>
          </button>
        </div>
        <div class="b-right empty" v-else>
          <div class="empty-ico">⚡</div>
          <p class="cd-label">暂无场次</p>
          <p class="cd-name">先去创建一场演示活动吧</p>
        </div>
      </div>
    </section>

    <div class="container main">
      <!-- 工具条 -->
      <div class="toolbar">
        <div class="t-left">
          <span class="t-bar"></span>
          <span class="t-label">场次时间轴</span>
        </div>
        <div class="filter-tabs">
          <button
            v-for="t in filterTabs"
            :key="t.key"
            class="ftab"
            :class="{ active: filter === t.key }"
            @click="filter = t.key"
          >
            {{ t.label }}
            <i v-if="t.key === 'live' && liveList.length" class="ftab-badge">{{ liveList.length }}</i>
            <i v-else-if="t.key === 'soon' && soonList.length" class="ftab-badge soon">{{ soonList.length }}</i>
          </button>
        </div>
        <div class="search">
          <span class="s-ico">⌕</span>
          <input v-model="keyword" class="field" type="search" placeholder="搜索秒杀商品 / 场次" />
        </div>
      </div>

      <!-- 场次时间轴 -->
      <div class="timeline" v-if="!loading && timeline.length">
        <button
          v-for="it in timeline"
          :key="it.activityId"
          class="tl"
          :class="tlPhaseClass(it)"
          @click="openIt(it)"
        >
          <b class="hnum">{{ hmOf(it) }}</b>
          <span class="tl-name">{{ (it.activityName || '').slice(0, 6) }}</span>
          <i>{{ { live: '抢购中', soon: '预告', over: '已结束' }[phaseOf(it)] }}</i>
        </button>
      </div>

      <div v-if="loading" class="grid-cards">
        <div v-for="i in 8" :key="i" class="skel panel">
          <div class="skeleton line" style="height: 220px"></div>
          <div class="skel-body">
            <div class="skeleton line" style="height: 15px; width: 80%"></div>
            <div class="skeleton line" style="height: 26px; width: 55%"></div>
          </div>
        </div>
      </div>

      <template v-else>
        <!-- 正在秒杀 -->
        <section class="sec" v-if="showSec('live') && liveList.length">
          <header class="sec-head">
            <h2><i class="live-dot"></i>正在秒杀<span class="sec-hl">爆款专区</span></h2>
            <span class="sec-count">共 {{ liveList.length }} 场</span>
          </header>
          <div class="grid-cards grid-featured">
            <ActivityCard
              v-for="(it, i) in liveList"
              :key="it.activityId"
              :item="it"
              :now="nowMs"
              :featured="i === 0 && liveList.length > 1"
            />
          </div>
        </section>

        <!-- 即将开抢 -->
        <section class="sec" v-if="showSec('soon') && soonList.length">
          <header class="sec-head">
            <h2><i class="soon-dot"></i>即将开抢<span class="sec-hl">预告专区</span></h2>
            <span class="sec-count">可预约，开抢自动提醒</span>
          </header>
          <div class="grid-cards">
            <ActivityCard v-for="it in soonList" :key="it.activityId" :item="it" :now="nowMs" />
          </div>
        </section>

        <!-- 已抢光回顾 -->
        <section class="sec over" v-if="showSec('over') && overList.length">
          <header class="sec-head">
            <h2><i class="over-dot"></i>已抢光<span class="sec-hl">回顾专区</span></h2>
            <span class="sec-count">共 {{ overList.length }} 场</span>
          </header>
          <div class="grid-cards">
            <ActivityCard v-for="it in overList" :key="it.activityId" :item="it" :now="nowMs" />
          </div>
        </section>

        <div v-if="(!showSec('live') || !liveList.length) && (!showSec('soon') || !soonList.length) && (!showSec('over') || !overList.length)" class="empty panel">
          <div class="empty-ico-lg">🔍</div>
          <h3>没有匹配的场次</h3>
          <p>换个关键词或筛选条件，或到管理控制台创建一场新秒杀。</p>
          <RouterLink class="btn btn-cta btn-sm" to="/admin">去创建</RouterLink>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.mall {
  min-height: 60vh;
}
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* ---------- Banner ---------- */
.banner {
  position: relative;
  overflow: hidden;
  background: linear-gradient(125deg, #0d0e14 0%, #1a0f1e 40%, #2a0f1a 70%, #1a0a12 100%);
  color: #fff;
}
.banner-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(700px 320px at 15% 30%, rgba(255, 45, 85, 0.5), transparent 60%),
    radial-gradient(560px 300px at 85% 80%, rgba(255, 138, 47, 0.32), transparent 55%);
  animation: glowShift 8s ease-in-out infinite alternate;
}
@keyframes glowShift {
  0% { transform: translateX(0) scale(1); }
  100% { transform: translateX(-20px) scale(1.05); }
}
.banner-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 90% 70% at 50% 40%, #000, transparent 80%);
}
.banner-inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 30px;
  padding-top: 52px;
  padding-bottom: 56px;
}
.b-left .eyebrow {
  margin: 0;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 4px;
  opacity: 0.92;
  background: linear-gradient(90deg, #ff5e3a, #ff2d55);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.b-left h1 {
  margin: 10px 0 0;
  font-size: 44px;
  line-height: 1.15;
  font-weight: 900;
  letter-spacing: 1px;
}
.b-left .hl {
  margin-left: 14px;
  font-size: 26px;
  font-weight: 800;
  padding: 4px 16px;
  border-radius: 999px;
  background: linear-gradient(120deg, rgba(255, 45, 85, 0.25), rgba(255, 94, 58, 0.25));
  border: 1px solid rgba(255, 45, 85, 0.4);
  vertical-align: middle;
}
.b-left .sub {
  margin: 12px 0 0;
  font-size: 14px;
  opacity: 0.72;
  line-height: 1.6;
}
.b-stats {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 18px;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat b {
  font-size: 24px;
  font-weight: 900;
  color: #fff;
  font-family: var(--font-num);
}
.stat span {
  font-size: 12px;
  opacity: 0.6;
}
.stat-sep {
  width: 1px;
  height: 30px;
  background: rgba(255, 255, 255, 0.15);
}
.cta-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 22px;
  padding: 11px 24px;
  background: rgba(255, 255, 255, 0.96);
  color: #ff2d55;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 800;
  transition: transform 0.18s var(--ease-out);
}
.cta-link:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px -6px rgba(255, 255, 255, 0.3);
}
.cta-link .arrow {
  transition: transform 0.18s;
}
.cta-link:hover .arrow {
  transform: translateX(3px);
}

.b-right {
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 22px 26px 24px;
  min-width: 320px;
  text-align: center;
  box-shadow: 0 20px 48px -16px rgba(0, 0, 0, 0.4);
}
.cd-head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 10px;
}
.cd-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.cd-dot.live {
  background: #34c759;
  box-shadow: 0 0 0 4px rgba(52, 199, 89, 0.25);
  animation: blink 1.3s ease infinite;
}
.cd-dot.soon {
  background: #ff9500;
  box-shadow: 0 0 0 4px rgba(255, 149, 0, 0.25);
}
@keyframes blink {
  50% { opacity: 0.3; }
}
.cd-label {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
  font-weight: 600;
}
.cd-block {
  margin: 6px 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.cd-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 38px;
  height: 52px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.04));
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #fff;
  border-radius: 10px;
  font-size: 32px;
  font-weight: 800;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
}
.cd-colon {
  font-size: 28px;
  font-weight: 800;
  opacity: 0.6;
}
.cd-name {
  margin: 0;
  font-size: 13px;
  opacity: 0.78;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.go-btn {
  margin-top: 14px;
  width: 100%;
  padding: 11px 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 12px;
  background: linear-gradient(120deg, #ff2d55, #ff5e3a);
  color: #fff;
  font-weight: 800;
  font-size: 15px;
  box-shadow: 0 6px 18px -4px rgba(255, 45, 85, 0.55);
  transition: transform 0.16s var(--ease-out), filter 0.16s;
}
.go-btn:hover {
  filter: brightness(1.06);
  transform: translateY(-1px);
}
.go-btn:active {
  transform: scale(0.98);
}
.go-arrow {
  transition: transform 0.16s;
}
.go-btn:hover .go-arrow {
  transform: translateX(3px);
}
.b-right.empty {
  color: rgba(255, 255, 255, 0.9);
}
.empty-ico {
  font-size: 36px;
  margin-bottom: 8px;
  filter: drop-shadow(0 0 12px rgba(255, 45, 85, 0.5));
}

/* ---------- 工具条 & 时间轴 ---------- */
.main {
  padding-bottom: 64px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin: 28px 0 4px;
}
.t-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.t-bar {
  width: 4px;
  height: 20px;
  background: var(--grad-cta);
  border-radius: 2px;
}
.t-label {
  font-size: 16px;
  font-weight: 800;
}
.search {
  position: relative;
  width: 280px;
}
.s-ico {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-3);
  font-size: 16px;
}
.search .field {
  padding-left: 38px;
  height: 44px;
  border-radius: 12px;
  background: var(--surface);
}

.timeline {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 16px 2px 18px;
  scrollbar-width: none;
}
.timeline::-webkit-scrollbar {
  display: none;
}
.tl {
  flex: none;
  width: 116px;
  border-radius: 14px;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  border: 1px solid var(--border);
  background: var(--surface);
  transition: transform 0.16s var(--ease-out), box-shadow 0.16s var(--ease-out);
}
.tl:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-2);
}
.tl b {
  font-size: 18px;
}
.tl .tl-name {
  font-size: 12px;
  color: var(--text-2);
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl i {
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 9px;
  border-radius: 999px;
}
.tl-live {
  background: linear-gradient(135deg, #ff2d55, #ff5e3a);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 8px 18px -4px rgba(255, 45, 85, 0.4);
}
.tl-live .tl-name {
  color: rgba(255, 255, 255, 0.92);
}
.tl-live i {
  background: #fff;
  color: #ff2d55;
}
.tl-soon b {
  color: #ff9500;
}
.tl-soon i {
  color: #ff9500;
  background: var(--warning-soft);
}
.tl-over {
  background: var(--surface-2);
  color: var(--text-3);
}
.tl-over i {
  background: var(--surface-3);
  color: var(--text-3);
}

/* ---------- 分区 ---------- */
.sec {
  margin-top: 24px;
}
.sec-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 0 0 14px;
}
.sec-head h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  display: flex;
  align-items: center;
  gap: 10px;
}
.sec-head .sec-hl {
  font-size: 14px;
  font-weight: 700;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 3px 10px;
  border-radius: 999px;
}
.sec-head .live-dot,
.sec-head .soon-dot,
.sec-head .over-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: none;
}
.sec-head .live-dot {
  background: #ff2d55;
  box-shadow: 0 0 0 4px rgba(255, 45, 85, 0.14);
  animation: blink 1.3s ease infinite;
}
.sec-head .soon-dot {
  background: #ff9500;
  box-shadow: 0 0 0 4px var(--warning-soft);
}
.sec-head .over-dot {
  background: var(--text-3);
  box-shadow: 0 0 0 4px var(--surface-3);
}
.sec-count {
  font-size: 13px;
  color: var(--text-3);
  font-weight: 500;
}
.sec.over {
  opacity: 0.85;
}
.grid-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(238px, 1fr));
  gap: 16px;
}
/* 精选大卡：首张横向跨 2 列 */
.grid-featured .acard.featured {
  grid-column: span 2;
}
.skel {
  overflow: hidden;
}
.skel-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* ---------- 筛选 Tab ---------- */
.filter-tabs {
  display: flex;
  gap: 4px;
  background: var(--surface-2);
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--border);
}
.ftab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
  transition: all 0.18s var(--ease-out);
}
.ftab:hover {
  color: var(--text);
}
.ftab.active {
  background: var(--surface);
  color: var(--accent);
  box-shadow: var(--shadow-1);
}
.ftab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  font-style: normal;
  color: #fff;
  background: linear-gradient(120deg, #ff2d55, #ff5e3a);
}
.ftab-badge.soon {
  background: var(--aux);
}

.empty {
  margin-top: 24px;
  padding: 64px 24px;
  text-align: center;
}
.empty-ico-lg {
  font-size: 48px;
  margin-bottom: 12px;
}
.empty h3 {
  margin: 0 0 6px;
}
.empty p {
  margin: 0 0 16px;
  color: var(--text-2);
  font-size: 14px;
}

@media (max-width: 760px) {
  .banner-inner {
    flex-direction: column;
    align-items: stretch;
    padding-top: 36px;
    padding-bottom: 40px;
  }
  .b-left h1 {
    font-size: 32px;
  }
  .b-left .hl {
    margin-left: 8px;
    font-size: 18px;
  }
  .b-right {
    min-width: 0;
  }
  .toolbar {
    gap: 10px;
  }
  .filter-tabs {
    width: 100%;
    overflow-x: auto;
    scrollbar-width: none;
  }
  .filter-tabs::-webkit-scrollbar {
    display: none;
  }
  .search {
    width: 100%;
  }
  .grid-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
  .grid-featured .acard.featured {
    grid-column: span 2;
  }
  .sec-head h2 {
    font-size: 18px;
  }
  .sec-head .sec-hl {
    display: none;
  }
}
@media (max-width: 400px) {
  .grid-cards {
    grid-template-columns: 1fr;
  }
  .cd-cell {
    min-width: 32px;
    height: 44px;
    font-size: 26px;
  }
}
</style>
