<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/apiClient'
import type { ActivityItem, ActivityMetrics } from '../types/api'
import { activityPreheatLabel, activityStatusLabel, formatDateTime, moneyFromCents, readableError } from '../utils/adminFormat'

const router = useRouter()
const activities = ref<ActivityItem[]>([])
const metrics = ref<ActivityMetrics | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const metricActivityId = ref('')
function activityLabel(status: string) { return status in activityStatusLabel ? activityStatusLabel[status as keyof typeof activityStatusLabel] : status }

const activeCount = computed(() => activities.value.filter((item) => item.status === 'RUNNING').length)
const pendingCount = computed(() => activities.value.filter((item) => item.status === 'NOT_STARTED').length)
const unheatedCount = computed(() => activities.value.filter((item) => item.preheated === false).length)
const totalStock = computed(() => activities.value.reduce((sum, item) => sum + (item.totalStock || 0), 0))

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''
  try {
    activities.value = (await api.listActivities()) ?? []
    const selected = activities.value.find((item) => item.status === 'RUNNING') ?? activities.value[0]
    metricActivityId.value = selected ? String(selected.activityId) : ''
    metrics.value = selected ? await api.getMetrics(String(selected.activityId)) : null
  } catch (error) {
    errorMessage.value = readableError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

async function loadMetrics(id: string) {
  metricActivityId.value = id
  metrics.value = null
  if (!id) return
  try {
    metrics.value = await api.getMetrics(id)
  } catch (error) {
    ElMessage.error(readableError(error))
  }
}

onMounted(() => void loadDashboard())
</script>

<template>
  <div class="page-view" v-loading="loading">
    <section class="page-heading">
      <div>
        <div class="eyebrow">工作台 · 运营概览</div>
        <h1>运营总览</h1>
        <p>查看秒杀活动状态与当前运行数据。</p>
      </div>
      <el-button type="primary" plain @click="loadDashboard"><span class="button-icon">↻</span>刷新数据</el-button>
    </section>

    <el-alert v-if="errorMessage" class="content-alert" :title="errorMessage" type="error" :closable="false" show-icon />

    <section class="stat-grid">
      <article class="stat-card"><div class="stat-label">活动总数</div><div class="stat-main"><strong>{{ activities.length }}</strong><span class="stat-icon icon-blue">活</span></div><div class="stat-foot">当前接口返回的全部活动</div></article>
      <article class="stat-card"><div class="stat-label">进行中</div><div class="stat-main"><strong>{{ activeCount }}</strong><span class="stat-icon icon-green">▶</span></div><div class="stat-foot">正在开放的秒杀场次</div></article>
      <article class="stat-card"><div class="stat-label">待开始</div><div class="stat-main"><strong>{{ pendingCount }}</strong><span class="stat-icon icon-amber">◷</span></div><div class="stat-foot">状态为未开始的活动</div></article>
      <article class="stat-card"><div class="stat-label">配置库存</div><div class="stat-main"><strong>{{ totalStock.toLocaleString('zh-CN') }}</strong><span class="stat-icon icon-violet">箱</span></div><div class="stat-foot">活动配置库存合计</div></article>
    </section>

    <section class="panel metrics-panel">
      <div class="panel-heading">
        <div><h2>活动运行指标</h2><p>指标按所选活动实时查询，不代表跨活动汇总。</p></div>
        <el-select v-if="activities.length" :model-value="metricActivityId" class="activity-select" placeholder="选择活动" @change="loadMetrics(String($event))">
          <el-option v-for="item in activities" :key="item.activityId" :label="`${item.activityName}（${item.activityId}）`" :value="String(item.activityId)" />
        </el-select>
        <el-button v-else disabled>暂无活动</el-button>
      </div>
      <div v-if="metrics" class="metric-grid">
        <div class="metric-item"><span>缓存剩余库存</span><strong>{{ metrics.redisStock ?? '—' }}</strong></div>
        <div class="metric-item"><span>订单数</span><strong>{{ metrics.orderCount }}</strong></div>
        <div class="metric-item"><span>队列积压</span><strong>{{ metrics.queuedMessageCount }}</strong></div>
        <div class="metric-item"><span>秒杀成功</span><strong class="text-success">{{ metrics.successCount }}</strong></div>
        <div class="metric-item"><span>重复请求拒绝</span><strong>{{ metrics.duplicateRejectCount }}</strong></div>
        <div class="metric-item"><span>限流拒绝</span><strong>{{ metrics.rateLimitRejectCount }}</strong></div>
        <div class="metric-item"><span>库存不足拒绝</span><strong>{{ metrics.soldOutRejectCount }}</strong></div>
      </div>
      <el-empty v-else :image-size="72" description="选择活动后查看运行指标" />
    </section>

    <section class="panel activity-panel">
      <div class="panel-heading">
        <div><h2>活动概况</h2><p>展示后端活动列表接口返回的数据。</p></div>
        <el-button text type="primary" @click="router.push('/activities')">管理活动 <span class="button-icon">→</span></el-button>
      </div>
      <el-table v-if="activities.length" :data="activities.slice(0, 6)" row-class-name="admin-table-row">
        <el-table-column prop="activityName" label="活动名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="productName" label="关联商品" min-width="150" show-overflow-tooltip />
        <el-table-column label="活动状态" width="120">
          <template #default="scope"><el-tag :type="scope.row.status === 'RUNNING' ? 'success' : scope.row.status === 'NOT_STARTED' ? 'warning' : 'info'" effect="light" round>{{ activityLabel(scope.row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="秒杀价" width="130"><template #default="scope">{{ moneyFromCents(scope.row.seckillPrice) }}</template></el-table-column>
        <el-table-column label="库存" width="100"><template #default="scope">{{ scope.row.stock }} / {{ scope.row.totalStock }}</template></el-table-column>
        <el-table-column label="预热状态" width="120"><template #default="scope"><span :class="scope.row.preheated === false ? 'text-warning' : 'text-success'">{{ activityPreheatLabel(scope.row.preheated) }}</span></template></el-table-column>
        <el-table-column label="开始时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.startTime) }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="router.push('/activities')">查看</el-button></template></el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" :image-size="82" description="暂无活动数据"><el-button type="primary" @click="router.push('/activities')">创建活动</el-button></el-empty>
    </section>

    <div class="overview-note"><span class="note-symbol">i</span><span>{{ unheatedCount ? `有 ${unheatedCount} 个活动尚未预热，活动开始前请在活动管理中完成预热。` : '活动与指标均由后端接口提供；总览页不使用模拟数据。' }}</span></div>
  </div>
</template>
