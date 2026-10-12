<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { api } from '../api/apiClient'
import type { ActivityDetail, ActivityItem, ActivityPayload, ActivityStatus } from '../types/api'
import { activityPreheatLabel, activityStatusLabel, centsFromYuan, formatDateTime, moneyFromCents, moneyToYuan, readableError } from '../utils/adminFormat'

interface ActivityForm {
  activityId: string
  productId: string
  activityName: string
  startTime: string
  endTime: string
  seckillPriceYuan: string
  seckillStock: number
}

const blankForm = (): ActivityForm => ({ activityId: '', productId: '', activityName: '', startTime: '', endTime: '', seckillPriceYuan: '', seckillStock: 1 })
const activities = ref<ActivityItem[]>([])
const loading = ref(false)
const busyId = ref('')
const busyAction = ref<'edit' | 'preheat' | 'reset' | null>(null)
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ActivityForm>(blankForm())
const keyword = ref('')
const statusFilter = ref<ActivityStatus | ''>('')
const editing = computed(() => Boolean(form.activityId))
let activitiesRequest: Promise<boolean> | null = null
function statusLabel(status: string) { return status in activityStatusLabel ? activityStatusLabel[status as ActivityStatus] : status }

const rules: FormRules<ActivityForm> = {
  productId: [{ required: true, message: '请输入关联商品编号', trigger: 'blur' }, { pattern: /^[1-9]\d*$/, message: '请输入正整数商品编号', trigger: 'blur' }],
  activityName: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  seckillPriceYuan: [{ required: true, message: '请输入秒杀价', trigger: 'blur' }, { validator: (_rule, value: string, callback) => {
    try { centsFromYuan(value); callback() } catch (error) { callback(error instanceof Error ? error : new Error('金额格式不正确')) }
  }, trigger: 'blur' }],
  seckillStock: [{ required: true, message: '请输入秒杀库存', trigger: 'change' }, { type: 'number', min: 1, message: '秒杀库存必须大于 0', trigger: 'change' }],
}

const filteredActivities = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  return activities.value.filter((item) => {
    const matchesText = !query || [item.activityName, item.productName, String(item.activityId), String(item.productId)].some((value) => value?.toLocaleLowerCase().includes(query))
    return matchesText && (!statusFilter.value || item.status === statusFilter.value)
  })
})

async function loadActivities(force = false): Promise<boolean> {
  if (activitiesRequest) {
    if (!force) return activitiesRequest
    await activitiesRequest
    if (activitiesRequest) return activitiesRequest
  }
  loading.value = true
  activitiesRequest = api.listActivities()
    .then((result) => { activities.value = result ?? []; return true })
    .catch((error: unknown) => { ElMessage.error(readableError(error)); return false })
    .finally(() => { loading.value = false; activitiesRequest = null })
  return activitiesRequest
}

function createActivity() {
  Object.assign(form, blankForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function editActivity(item: ActivityItem) {
  if (busyId.value) return
  busyId.value = String(item.activityId)
  busyAction.value = 'edit'
  try {
    const detail: ActivityDetail = await api.getActivity(String(item.activityId))
    Object.assign(form, {
      activityId: String(detail.activityId), productId: String(detail.productId), activityName: detail.activityName,
      startTime: formatDateTime(detail.startTime), endTime: formatDateTime(detail.endTime),
      seckillPriceYuan: moneyToYuan(detail.seckillPrice), seckillStock: detail.totalStock,
    })
    dialogVisible.value = true
  } catch (error) { ElMessage.error(readableError(error)) }
  finally { busyId.value = ''; busyAction.value = null }
}

async function saveActivity() {
  if (!await formRef.value?.validate().catch(() => false)) return
  if (new Date(form.startTime.replace(' ', 'T')).getTime() >= new Date(form.endTime.replace(' ', 'T')).getTime()) {
    ElMessage.warning('结束时间必须晚于开始时间。')
    return
  }
  saving.value = true
  try {
    const payload: ActivityPayload = {
      ...(form.activityId ? { activityId: form.activityId } : {}),
      productId: form.productId.trim(), activityName: form.activityName.trim(),
      startTime: form.startTime, endTime: form.endTime,
      seckillPrice: centsFromYuan(form.seckillPriceYuan), seckillStock: form.seckillStock,
      limitPerUser: 1,
    }
    const result = await api.saveActivity(payload)
    ElMessage.success(`活动${form.activityId ? '已更新' : '已创建'}，编号 ${result.activityId}。`)
    dialogVisible.value = false
    await loadActivities()
  } catch (error) { ElMessage.error(readableError(error)) }
  finally { saving.value = false }
}

async function runCacheAction(item: ActivityItem, action: 'preheat' | 'reset') {
  if (busyId.value) return
  const id = String(item.activityId)
  busyId.value = id
  busyAction.value = action
  try {
    const isPreheat = action === 'preheat'
    await ElMessageBox.confirm(
      isPreheat
        ? `确认预热活动“${item.activityName}”并写入活动信息与库存缓存吗？`
        : `确认将活动“${item.activityName}”的缓存库存恢复为配置库存吗？活动进行中不可重置。`,
      isPreheat ? '预热活动缓存' : '重置缓存库存',
      { type: 'warning', confirmButtonText: isPreheat ? '确认预热' : '确认重置', cancelButtonText: '取消' },
    )
    const result = isPreheat ? await api.preheat(id) : await api.resetStock(id)
    if (String(result.activityId) !== id || !Number.isInteger(result.stock) || result.stock < 0) {
      throw new Error('后端返回的活动编号或缓存库存格式不正确。')
    }
    if (!await loadActivities(true)) {
      ElMessage.success(isPreheat ? `后端已完成预热，缓存库存 ${result.stock}。` : `后端已重置缓存库存为 ${result.stock}。`)
      ElMessage.warning('活动列表刷新失败，请稍后手动刷新以确认最新状态。')
      return
    }
    const refreshed = activities.value.find((activity) => String(activity.activityId) === id)
    if (!refreshed) {
      ElMessage.warning('操作接口已返回成功，但刷新后的活动列表中未找到该活动。')
    } else if (isPreheat && refreshed.preheated !== true) {
      ElMessage.warning('预热接口已返回成功，但活动列表仍显示缓存未就绪；请检查缓存服务后重试。')
    } else if (refreshed.stock !== result.stock) {
      ElMessage.warning(`接口返回库存为 ${result.stock}，列表刷新后为 ${refreshed.stock}；请再次刷新确认。`)
    } else {
      ElMessage.success(isPreheat ? `预热完成，缓存库存 ${result.stock}。` : `缓存库存已恢复为 ${result.stock}。`)
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(readableError(error))
  } finally { busyId.value = ''; busyAction.value = null }
}

const preheat = (item: ActivityItem) => runCacheAction(item, 'preheat')
const resetStock = (item: ActivityItem) => runCacheAction(item, 'reset')

function statusType(status: ActivityStatus) {
  if (status === 'RUNNING') return 'success'
  if (status === 'NOT_STARTED') return 'warning'
  if (status === 'CANCELLED') return 'danger'
  return 'info'
}

onMounted(() => void loadActivities())
</script>

<template>
  <div class="page-view">
    <section class="page-heading">
      <div><div class="eyebrow">商品与活动</div><h1>秒杀活动</h1><p>查询活动、配置场次并管理缓存预热与库存。</p></div>
      <el-button type="primary" @click="createActivity"><span class="button-icon">＋</span>创建活动</el-button>
    </section>

    <section class="filter-bar">
      <el-input v-model="keyword" class="filter-search" clearable placeholder="搜索活动名称、商品或编号"><template #prefix><span class="search-symbol">⌕</span></template></el-input>
      <el-select v-model="statusFilter" clearable placeholder="全部状态" class="status-select"><el-option v-for="(label, value) in activityStatusLabel" :key="value" :label="label" :value="value" /></el-select>
      <el-button :loading="loading" @click="loadActivities()">刷新</el-button>
      <span class="result-count">共 {{ filteredActivities.length }} 个活动</span>
    </section>

    <section class="panel table-panel" v-loading="loading">
      <el-table v-if="filteredActivities.length" :data="filteredActivities" row-class-name="admin-table-row" table-layout="auto">
        <el-table-column prop="activityName" label="活动名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="活动编号" min-width="130"><template #default="scope"><span class="mono-text">{{ scope.row.activityId }}</span></template></el-table-column>
        <el-table-column label="关联商品" min-width="170"><template #default="scope"><div class="product-cell"><div class="product-thumb"><img v-if="scope.row.productImage" :src="scope.row.productImage" alt="" /><span v-else>品</span></div><div><strong>{{ scope.row.productName || '未命名商品' }}</strong><small>编号 {{ scope.row.productId }}</small></div></div></template></el-table-column>
        <el-table-column label="活动状态" width="112"><template #default="scope"><el-tag :type="statusType(scope.row.status)" effect="light" round>{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="秒杀价" width="105"><template #default="scope"><strong class="price-text">{{ moneyFromCents(scope.row.seckillPrice) }}</strong></template></el-table-column>
        <el-table-column label="剩余库存" width="115"><template #default="scope">{{ scope.row.stock }} <span class="muted-text">/ {{ scope.row.totalStock }}</span></template></el-table-column>
        <el-table-column label="预热" width="105"><template #default="scope"><span :class="scope.row.preheated === false ? 'text-warning' : 'text-success'"><i class="tiny-status-dot"></i>{{ activityPreheatLabel(scope.row.preheated) }}</span></template></el-table-column>
        <el-table-column label="活动时间" min-width="182"><template #default="scope"><div>{{ formatDateTime(scope.row.startTime) }}</div><small class="muted-text">至 {{ formatDateTime(scope.row.endTime) }}</small></template></el-table-column>
        <el-table-column label="操作" min-width="210" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :loading="busyId === String(scope.row.activityId) && busyAction === 'edit'" :disabled="busyId === String(scope.row.activityId) && busyAction !== 'edit'" @click="editActivity(scope.row as ActivityItem)">编辑</el-button>
            <el-button link type="primary" :loading="busyId === String(scope.row.activityId) && busyAction === 'preheat'" :disabled="busyId === String(scope.row.activityId) && busyAction !== 'preheat'" @click="preheat(scope.row as ActivityItem)">预热</el-button>
            <el-button link type="warning" :disabled="scope.row.status === 'RUNNING' || (busyId === String(scope.row.activityId) && busyAction !== 'reset')" :loading="busyId === String(scope.row.activityId) && busyAction === 'reset'" @click="resetStock(scope.row as ActivityItem)">重置缓存库存</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" :image-size="92" :description="activities.length ? '没有符合筛选条件的活动' : '暂无活动，可创建第一场秒杀活动'"><el-button v-if="!activities.length" type="primary" @click="createActivity">创建活动</el-button></el-empty>
    </section>

    <div class="overview-note"><span class="note-symbol">i</span><span>活动列表由用户端活动查询接口提供；删除、批量操作和服务端分页接口当前不可用。</span></div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑秒杀活动' : '创建秒杀活动'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="dialog-form">
        <el-form-item label="活动名称" prop="activityName"><el-input v-model="form.activityName" placeholder="输入活动名称" /></el-form-item>
        <el-form-item label="关联商品编号" prop="productId"><el-input v-model="form.productId" placeholder="输入已存在的商品编号" /></el-form-item>
        <el-form-item label="开始时间" prop="startTime"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm:ss" placeholder="选择开始时间" /></el-form-item>
        <el-form-item label="结束时间" prop="endTime"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm:ss" placeholder="选择结束时间" /></el-form-item>
        <el-form-item label="秒杀价（元）" prop="seckillPriceYuan"><el-input v-model="form.seckillPriceYuan" inputmode="decimal" placeholder="例如 59.90"><template #prepend>¥</template></el-input></el-form-item>
        <el-form-item label="秒杀库存" prop="seckillStock"><el-input-number v-model="form.seckillStock" :min="1" :max="100000000" controls-position="right" /></el-form-item>
        <el-form-item label="每位用户限购"><el-input model-value="1 件（系统规则）" disabled /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveActivity">保存活动</el-button></template>
    </el-dialog>
  </div>
</template>
