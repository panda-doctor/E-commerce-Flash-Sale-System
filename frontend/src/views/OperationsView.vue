<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/apiClient'
import type { ActivityItem, ActivityMetrics } from '../types/api'
import { readableError } from '../utils/adminFormat'

const activities = ref<ActivityItem[]>([])
const selectedId = ref('')
const metrics = ref<ActivityMetrics | null>(null)
const messageId = ref('')
const loadingActivities = ref(false)
const loadingMetrics = ref(false)
const savingSnapshot = ref(false)
const replaying = ref(false)
const lastUpdated = ref('')
let refreshTimer: ReturnType<typeof setInterval> | undefined

const selectedActivity = computed(() => activities.value.find((item) => String(item.activityId) === selectedId.value))

async function loadActivities() {
  loadingActivities.value = true
  try {
    activities.value = (await api.listActivities()) ?? []
    if (!selectedId.value && activities.value.length) {
      const preferred = activities.value.find((item) => item.status === 'RUNNING') ?? activities.value[0]
      selectedId.value = String(preferred.activityId)
    }
  } catch (error) { ElMessage.error(readableError(error)) }
  finally { loadingActivities.value = false }
}

async function loadMetrics(showError = true) {
  if (!selectedId.value) { metrics.value = null; return }
  loadingMetrics.value = true
  try {
    metrics.value = await api.getMetrics(selectedId.value)
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (error) {
    metrics.value = null
    if (showError) ElMessage.error(readableError(error))
  } finally { loadingMetrics.value = false }
}

async function saveSnapshot() {
  if (!selectedId.value) return
  try {
    await ElMessageBox.confirm('将当前活动运行指标保存为一条快照记录吗？', '保存指标快照', { type: 'info', confirmButtonText: '保存快照', cancelButtonText: '取消' })
    savingSnapshot.value = true
    await api.snapshot(selectedId.value)
    ElMessage.success('指标快照已保存。')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(readableError(error))
  } finally { savingSnapshot.value = false }
}

async function replay() {
  const id = messageId.value.trim()
  if (!id) { ElMessage.warning('请输入原始消息编号。'); return }
  try {
    await ElMessageBox.confirm(`确认重新投递消息 ${id} 吗？`, '死信回放', { type: 'warning', confirmButtonText: '确认回放', cancelButtonText: '取消' })
    replaying.value = true
    const result = await api.replayDeadLetter(id)
    ElMessage.success(`消息已重新投递，新消息编号：${result.replayMessageId}`)
    messageId.value = ''
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(readableError(error))
  } finally { replaying.value = false }
}

watch(selectedId, () => void loadMetrics())
onMounted(async () => {
  await loadActivities()
  void loadMetrics()
  refreshTimer = setInterval(() => { if (!document.hidden) void loadMetrics(false) }, 15000)
})
onBeforeUnmount(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<template>
  <div class="page-view">
    <section class="page-heading">
      <div><div class="eyebrow">系统运维</div><h1>运行监控</h1><p>查看活动库存、异步队列及秒杀请求处理指标。</p></div>
      <div class="heading-actions"><el-button :loading="loadingMetrics" @click="loadMetrics()">立即刷新</el-button><el-button type="primary" :disabled="!selectedId" :loading="savingSnapshot" @click="saveSnapshot">保存指标快照</el-button></div>
    </section>

    <section class="panel monitor-toolbar">
      <div><div class="toolbar-label">监控活动</div><div class="toolbar-hint">指标每 15 秒自动刷新</div></div>
      <el-select v-model="selectedId" :loading="loadingActivities" class="activity-select" placeholder="选择活动" @change="loadMetrics()">
        <el-option v-for="item in activities" :key="item.activityId" :label="`${item.activityName}（${item.activityId}）`" :value="String(item.activityId)" />
      </el-select>
      <span v-if="selectedActivity" class="monitor-state"><i :class="selectedActivity.status === 'RUNNING' ? 'state-live' : 'state-idle'"></i>{{ selectedActivity.status === 'RUNNING' ? '活动进行中' : '活动未运行' }}</span>
      <span v-if="lastUpdated" class="muted-text">更新于 {{ lastUpdated }}</span>
    </section>

    <section v-loading="loadingMetrics" class="monitor-grid">
      <article class="monitor-card monitor-stock"><div class="monitor-card-label">缓存剩余库存</div><strong>{{ metrics?.redisStock ?? '—' }}</strong><span>Redis 当前库存</span><i class="monitor-decoration">库</i></article>
      <article class="monitor-card"><div class="monitor-card-label">订单总数</div><strong>{{ metrics?.orderCount ?? '—' }}</strong><span>已记录订单</span><i class="monitor-decoration">单</i></article>
      <article class="monitor-card"><div class="monitor-card-label">队列积压</div><strong>{{ metrics?.queuedMessageCount ?? '—' }}</strong><span>待消费消息</span><i class="monitor-decoration">队</i></article>
      <article class="monitor-card monitor-success"><div class="monitor-card-label">秒杀成功</div><strong>{{ metrics?.successCount ?? '—' }}</strong><span>成功创建订单</span><i class="monitor-decoration">成</i></article>
    </section>

    <section class="panel rejection-panel">
      <div class="panel-heading"><div><h2>请求拒绝统计</h2><p>当前活动的业务拒绝计数。</p></div><span class="live-caption"><i></i>接口轮询</span></div>
      <div class="rejection-grid">
        <div><span class="reject-badge reject-amber">重</span><div><small>重复请求</small><strong>{{ metrics?.duplicateRejectCount ?? '—' }}</strong></div></div>
        <div><span class="reject-badge reject-blue">限</span><div><small>频率限制</small><strong>{{ metrics?.rateLimitRejectCount ?? '—' }}</strong></div></div>
        <div><span class="reject-badge reject-red">售</span><div><small>库存不足</small><strong>{{ metrics?.soldOutRejectCount ?? '—' }}</strong></div></div>
      </div>
    </section>

    <section class="panel replay-panel">
      <div class="panel-heading"><div><h2>死信消息回放</h2><p>重新投递一条已补偿的原始消息。该操作会创建新的队列消息。</p></div><span class="danger-label">谨慎操作</span></div>
      <div class="replay-form"><el-input v-model="messageId" placeholder="输入原始消息编号，例如 1720000000000-0" clearable @keyup.enter="replay" /><el-button type="warning" :loading="replaying" @click="replay">回放消息</el-button></div>
      <div class="field-hint">回放结果会显示新的消息编号；请确认原消息状态及业务影响后再操作。</div>
    </section>

    <div class="overview-note"><span class="note-symbol">i</span><span>后端未提供历史指标查询接口，因此此页展示当前指标与人工触发的快照保存，不绘制历史趋势图。</span></div>
  </div>
</template>
