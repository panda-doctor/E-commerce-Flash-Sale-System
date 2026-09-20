<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import { toastErr, toastOK } from '../utils/toast'

// 运行指标面板：轮询 /api/admin/.../metrics，展示活动实时状态
const props = defineProps({
  activityId: { type: [String, Number], required: true },
})

const m = ref(null) // ActivityMetricsVO
let timer = null
const snapshotting = ref(false)

async function refresh() {
  if (!props.activityId || document.hidden) return
  try {
    m.value = await api.getMetrics(props.activityId)
  } catch (e) {
    // 指标不可得时保留旧值/置空（如活动不存在），不打扰用户
    m.value = null
  }
}

async function doSnapshot() {
  snapshotting.value = true
  try {
    await api.snapshot(props.activityId)
    toastOK('指标快照已写入 seckill_activity_snapshot')
  } catch (e) {
    toastErr(e.message || '打点失败')
  } finally {
    snapshotting.value = false
  }
}

watch(() => props.activityId, () => { m.value = null; refresh() })
onMounted(() => {
  refresh()
  timer = setInterval(refresh, 4000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <section class="panel metrics" aria-label="活动运行指标">
    <header class="metrics-head">
      <h3>
        运行指标
        <span class="live">LIVE</span>
      </h3>
      <button class="snap-btn" :disabled="snapshotting || !activityId" @click="doSnapshot">
        <span class="snap-ico">◉</span>
        {{ snapshotting ? '写入中…' : '打点快照' }}
      </button>
    </header>

    <div v-if="!m" class="placeholder">
      <span class="skeleton line" style="width: 100%; height: 68px; border-radius: 12px"></span>
    </div>

    <div v-else class="grid">
      <div class="cell" :class="{ warn: (m.redisStock ?? 99) === 0 }">
        <div class="cell-top">
          <span class="k">缓存库存</span>
          <span v-if="(m.redisStock ?? -1) === 0" class="badge badge-danger">已售罄</span>
          <span v-else-if="m.redisStock == null" class="badge">未预热</span>
        </div>
        <b class="v hnum">{{ m.redisStock ?? '—' }}</b>
      </div>

      <div class="cell ok">
        <div class="cell-top">
          <span class="k">成功订单</span>
        </div>
        <b class="v hnum">{{ m.successCount ?? 0 }}</b>
      </div>

      <div class="cell" :class="{ warn: (m.queuedMessageCount ?? 0) > 0 }">
        <div class="cell-top">
          <span class="k">队列积压</span>
        </div>
        <b class="v hnum">{{ m.queuedMessageCount ?? 0 }}</b>
      </div>

      <div class="cell">
        <div class="cell-top">
          <span class="k">限流拒绝</span>
        </div>
        <b class="v hnum">{{ m.rateLimitRejectCount ?? 0 }}</b>
      </div>

      <div class="cell">
        <div class="cell-top">
          <span class="k">重复拒绝</span>
        </div>
        <b class="v hnum">{{ m.duplicateRejectCount ?? 0 }}</b>
      </div>

      <div class="cell">
        <div class="cell-top">
          <span class="k">售罄拒绝</span>
        </div>
        <b class="v hnum">{{ m.soldOutRejectCount ?? 0 }}</b>
      </div>
    </div>
  </section>
</template>

<style scoped>
.metrics {
  padding: 18px 22px 22px;
}
.metrics-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.metrics-head h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.live {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1px;
  color: #fff;
  background: var(--danger);
  border-radius: 5px;
  padding: 2px 7px;
  animation: pulse 1.6s ease infinite;
}
@keyframes pulse {
  50% { opacity: 0.55; }
}

.snap-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--info);
  background: var(--info-soft);
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(37, 99, 235, 0.15);
  transition: all 0.15s var(--ease-out);
}
.snap-ico {
  font-size: 11px;
}
.snap-btn:hover:not(:disabled) {
  filter: brightness(0.96);
  transform: translateY(-1px);
}
.snap-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.placeholder {
  height: 68px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}
.cell {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  transition: transform 0.16s var(--ease-out);
}
.cell:hover {
  transform: translateY(-2px);
}
.cell-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.k {
  font-size: 12px;
  color: var(--text-2);
  font-weight: 600;
}
.v {
  font-size: 28px;
  font-weight: 900;
  font-family: var(--font-num);
  color: var(--text);
  line-height: 1;
}
.cell.ok {
  background: var(--success-soft);
  border-color: rgba(52, 199, 89, 0.18);
}
.cell.ok .v {
  color: var(--success);
}
.cell.warn {
  background: var(--warning-soft);
  border-color: rgba(245, 158, 11, 0.2);
}
.cell.warn .v {
  color: var(--warning);
}
.badge {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-2);
  background: var(--surface-3);
  padding: 2px 8px;
  border-radius: 999px;
}
.badge-danger {
  color: var(--danger);
  background: var(--danger-soft);
}
</style>
