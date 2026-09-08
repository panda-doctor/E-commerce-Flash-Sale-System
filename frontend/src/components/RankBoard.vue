<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import { userStore } from '../utils/store'

// 排行榜面板：按活动轮询 /api/rank/top10
// score 语义 = 抢单成功时刻（毫秒），展示为时间戳更直观
const props = defineProps({
  activityId: { type: Number, required: true },
})

const items = ref([])
const loading = ref(false)
const updatedAt = ref('')
let timer = null

async function refresh() {
  if (!props.activityId) return
  loading.value = items.value.length === 0
  try {
    const data = await api.getRank(props.activityId, 10)
    items.value = Array.isArray(data) ? data : []
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (e) {
    items.value = [] // 榜键不存在返回空，视为正常
  } finally {
    loading.value = false
  }
}

function timeText(score) {
  if (!score) return '--:--:--'
  const d = new Date(score)
  if (Number.isNaN(d.getTime())) return '--:--:--'
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const isMe = (uid) => uid === userStore.userId

watch(() => props.activityId, refresh)
onMounted(() => {
  refresh()
  timer = setInterval(refresh, 3000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <section class="panel board" aria-label="秒杀成功排行榜">
    <header class="board-head">
      <div class="board-title">
        <span class="trophy">&#127942;</span>
        <h3>实时手速榜</h3>
        <span class="board-badge">TOP 10</span>
      </div>
      <span v-if="updatedAt" class="updated">
        <span class="up-dot"></span>{{ updatedAt }}
      </span>
    </header>

    <div v-if="loading" class="body">
      <div v-for="i in 5" :key="i" class="skeleton line" style="height: 44px; border-radius: 10px"></div>
    </div>

    <ol v-else-if="items.length" class="board-list">
      <li
        v-for="(it, idx) in items"
        :key="it.userId"
        class="row"
        :class="{ top3: idx < 3, me: isMe(it.userId) }"
        :style="{ animationDelay: idx * 40 + 'ms' }"
      >
        <span class="rank" :class="`rk-${idx + 1}`">{{ it.rank }}</span>
        <span class="who">
          <b>用户 {{ it.userId }}</b>
          <em v-if="isMe(it.userId)" class="you">我</em>
        </span>
        <span class="no hnum">{{ it.orderNo ? it.orderNo.slice(-8) : '--' }}</span>
        <time class="at hnum">{{ timeText(it.score) }}</time>
      </li>
    </ol>

    <div v-else class="empty">
      <div class="empty-trophy">&#127881;</div>
      <p>还没有人抢到</p>
      <p class="empty-sub">做第一个上榜的人？</p>
    </div>
  </section>
</template>

<style scoped>
.board {
  overflow: hidden;
}
.board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--border);
}
.board-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.trophy {
  font-size: 20px;
  filter: saturate(1.1);
}
.board-title h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
}
.board-badge {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 2px 7px;
  border-radius: 5px;
}
.updated {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-3);
  font-family: var(--font-num);
}
.up-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
  animation: blink 1.6s ease infinite;
}
@keyframes blink {
  50% { opacity: 0.3; }
}

.body {
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.board-list {
  list-style: none;
  margin: 0;
  padding: 10px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.row {
  display: grid;
  grid-template-columns: 36px 1fr 90px 84px;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  opacity: 0;
  animation: rise 0.3s var(--ease-out) forwards;
  transition: background 0.16s, transform 0.16s;
}
@keyframes rise {
  to { opacity: 1; }
}
.row:hover {
  background: var(--surface-2);
  transform: translateX(2px);
}
.row:nth-child(even) {
  background: var(--surface-2);
}
.row.me {
  background: var(--info-soft);
  box-shadow: inset 0 0 0 1.5px rgba(37, 99, 235, 0.25);
}
.row.me:hover {
  transform: translateX(2px);
}

.rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  color: var(--text-2);
  background: var(--surface-3);
}
.rk-1 {
  background: linear-gradient(135deg, #fbbf24, #ff9500);
  color: #7c2d12;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.25);
}
.rk-2 {
  background: linear-gradient(135deg, #d1d5db, #9ca3af);
  color: #1f2937;
  box-shadow: 0 0 0 3px rgba(156, 163, 175, 0.2);
}
.rk-3 {
  background: linear-gradient(135deg, #d6a877, #b07a45);
  color: #431407;
  box-shadow: 0 0 0 3px rgba(176, 122, 69, 0.2);
}

.who {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.who b {
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.you {
  flex: none;
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
  color: var(--info);
  background: var(--info-soft);
  padding: 1px 7px;
  border-radius: 999px;
}
.no {
  font-size: 12px;
  color: var(--text-3);
  text-align: right;
  font-family: var(--font-num);
}
.at {
  font-size: 13px;
  color: var(--text-2);
  text-align: right;
  font-family: var(--font-num);
  font-weight: 600;
}

.empty {
  padding: 40px 16px;
  text-align: center;
}
.empty-trophy {
  font-size: 36px;
  margin-bottom: 10px;
}
.empty p {
  margin: 0;
  color: var(--text-2);
  font-size: 15px;
  font-weight: 600;
}
.empty-sub {
  margin-top: 2px !important;
  color: var(--text-3) !important;
  font-size: 13px !important;
  font-weight: 500 !important;
}

@media (max-width: 560px) {
  .row {
    grid-template-columns: 30px 1fr 70px;
  }
  .no {
    display: none;
  }
}
</style>
