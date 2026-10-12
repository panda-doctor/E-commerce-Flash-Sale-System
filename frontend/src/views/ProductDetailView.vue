<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/apiClient'
import { ApiError } from '../types/api'
import type { ActivityItem, ProductDetail } from '../types/api'
import ProductImage from '../components/ProductImage.vue'
import { authState, bootstrapSession } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const product = ref<ProductDetail | null>(null)
const activities = ref<ActivityItem[]>([])
const loading = ref(true)
const buying = ref(false)
const error = ref('')
const chosenActivityId = ref('')
const currentTime = ref(Date.now())
let activityRefreshTimer: ReturnType<typeof setInterval> | undefined

const productActivities = computed(() => activities.value.filter(item => item.productId === String(product.value?.productId)))
const selectedActivity = computed(() => productActivities.value.find(item => item.activityId === chosenActivityId.value) ?? productActivities.value[0])
const availablePercent = computed(() => {
  const activity = selectedActivity.value
  return activity && activity.totalStock > 0 ? Math.min(100, Math.max(0, Math.round(activity.stock / activity.totalStock * 100))) : 0
})

onMounted(() => {
  void loadProduct()
  activityRefreshTimer = setInterval(() => {
    currentTime.value = Date.now()
    if (!loading.value) void loadProduct(true)
  }, 20000)
})
onBeforeUnmount(() => { if (activityRefreshTimer) clearInterval(activityRefreshTimer) })
watch(() => [route.params.productId, route.query.activity], () => { void loadProduct() })

async function loadProduct(silent = false) {
  if (!silent) {
    loading.value = true
    error.value = ''
    product.value = null
  }
  try {
    const productId = String(route.params.productId)
    const [detail, allActivities] = await Promise.all([api.getProduct(productId), api.listActivities()])
    product.value = detail
    activities.value = allActivities
    const requestedId = typeof route.query.activity === 'string' ? route.query.activity : ''
    const options = allActivities.filter(item => item.productId === String(detail.productId))
    chosenActivityId.value = options.find(item => item.activityId === requestedId)?.activityId
      ?? options.find(item => item.activityId === chosenActivityId.value)?.activityId
      ?? options.find(item => displayStatus(item) === 'RUNNING')?.activityId
      ?? options[0]?.activityId ?? ''
  } catch (cause) {
    if (!silent) error.value = cause instanceof Error ? cause.message : '商品暂时无法加载。'
  } finally {
    if (!silent) loading.value = false
  }
}

function money(cents: number): string { return `¥${(cents / 100).toFixed(2)}` }

function dateTime(value: string): string {
  return new Date(value.replace(' ', 'T')).toLocaleString('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
}

function displayStatus(item: ActivityItem): string {
  if (item.status === 'CANCELLED' || item.status === 'SOLD_OUT') return item.status
  const start = new Date(item.startTime.replace(' ', 'T')).getTime()
  const end = new Date(item.endTime.replace(' ', 'T')).getTime()
  if (!Number.isNaN(start) && !Number.isNaN(end)) {
    if (currentTime.value < start) return 'NOT_STARTED'
    if (currentTime.value >= end) return 'ENDED'
    if (item.stock <= 0) return 'SOLD_OUT'
    return 'RUNNING'
  }
  return item.status
}

function statusText(item: ActivityItem): string {
  const state = displayStatus(item)
  return ({ RUNNING: '正在抢购', NOT_STARTED: '即将开始', ENDED: '活动已结束', SOLD_OUT: '已售罄', CANCELLED: '活动已取消' } as Record<string, string>)[state] ?? '活动状态'
}

const canPurchase = computed(() => selectedActivity.value != null
  && displayStatus(selectedActivity.value) === 'RUNNING')

async function startPurchase() {
  if (!selectedActivity.value || buying.value) return
  await bootstrapSession()
  if (!authState.user) {
    ElMessage.info('请先登录账号，再参与秒杀活动。')
    await router.push({ name: 'sign-in', query: { redirect: route.fullPath } })
    return
  }
  if (!canPurchase.value) {
    ElMessage.info(statusText(selectedActivity.value))
    return
  }
  buying.value = true
  try {
    const eligibility = await api.checkActivity(selectedActivity.value.activityId)
    if (!eligibility.canJoin) {
      ElMessage.warning(({ ACTIVITY_NOT_PREHEATED: '活动库存正在准备。', ACTIVITY_NOT_STARTED: '活动尚未开始。', ACTIVITY_ENDED: '活动已经结束。', ACTIVITY_SOLD_OUT: '商品已售罄。', ACTIVITY_CANCELLED: '活动已取消。' } as Record<string, string>)[eligibility.reason] ?? '当前暂时无法参与活动。')
      await loadProduct()
      return
    }
    const result = await api.executeSeckill(selectedActivity.value.activityId)
    if (!result.orderNo) {
      ElMessage.success('请求已受理。')
      await loadProduct()
      return
    }
    let finalStatus = 'QUEUING'
    for (let attempt = 0; attempt < 10; attempt += 1) {
      const order = await api.getOrderStatus(result.orderNo)
      finalStatus = order.status
      if (order.status !== 'QUEUING') break
      await new Promise(resolve => window.setTimeout(resolve, 1200))
    }
    if (finalStatus === 'CREATED') {
      ElMessage.success('抢购成功，订单已创建。')
      await router.push('/account/orders')
    } else if (finalStatus === 'FAILED') {
      ElMessage.warning('订单创建未成功，请查看活动状态后重试。')
      await loadProduct()
    } else {
      ElMessage.info('订单仍在处理中，可在“我的订单”继续查看。')
      await router.push('/account/orders')
    }
  } catch (cause) {
    const message = cause instanceof ApiError
      ? (cause.code === 40901 ? '你已参与过这场活动。' : cause.code === 42900 ? '操作有些频繁，请稍后再试。' : cause.message)
      : cause instanceof Error ? cause.message : '提交失败，请稍后再试。'
    ElMessage.error(message)
  } finally {
    buying.value = false
  }
}
</script>

<template>
  <div class="shop-container product-detail-page">
    <div class="detail-breadcrumb"><RouterLink to="/">首页</RouterLink><span class="breadcrumb-divider" aria-hidden="true">›</span><span>商品详情</span><span v-if="product" class="breadcrumb-divider" aria-hidden="true">›</span><strong v-if="product">{{ product.name }}</strong></div>
    <div v-if="loading" class="detail-loading"><span></span><span></span><span></span></div>
    <div v-else-if="error" class="shop-state detail-error"><div class="state-icon">!</div><h3>暂时无法显示商品</h3><p>{{ error }}</p><button class="solid-button" type="button" @click="loadProduct()">重新加载</button></div>
    <div v-else-if="product" class="detail-layout">
      <section class="detail-gallery">
        <div class="detail-image"><ProductImage :src="product.imageUrl" :alt="product.name" variant="detail" loading="eager" /></div>
      </section>
      <section class="detail-copy">
        <h1>{{ product.name }}</h1>
        <p class="detail-description">{{ product.description || '商品介绍暂未填写。' }}</p>
        <div v-if="selectedActivity" class="detail-sale-box">
          <div class="sale-box-top"><span class="product-status" :class="displayStatus(selectedActivity).toLowerCase()"><i></i>{{ statusText(selectedActivity) }}</span><span class="sale-limit">每位用户限购 {{ selectedActivity.limitPerUser }} 件</span></div>
          <div class="detail-price"><strong>{{ money(selectedActivity.seckillPrice) }}</strong><del>{{ money(product.originalPrice) }}</del><span v-if="product.originalPrice > selectedActivity.seckillPrice">活动优惠</span></div>
          <div class="detail-activity-name">{{ selectedActivity.activityName }}</div>
          <div class="detail-time"><span>活动时间</span><strong>{{ dateTime(selectedActivity.startTime) }} — {{ dateTime(selectedActivity.endTime) }}</strong></div>
          <div class="detail-stock"><div><span>剩余库存</span><strong>{{ selectedActivity.stock }} <small>件</small></strong></div><div class="detail-stock-track"><span :style="{ width: `${availablePercent}%` }"></span></div></div>
          <button class="purchase-button" type="button" :disabled="buying || !canPurchase" @click="startPurchase">{{ buying ? '正在确认活动资格…' : canPurchase ? '立即参加秒杀' : statusText(selectedActivity) }}</button>
        </div>
        <div v-else class="detail-no-activity"><span class="no-activity-mark">◷</span><div><strong>当前暂无秒杀活动</strong><p>商品仍可浏览，新的活动开始后会显示在这里。</p></div></div>
        <div v-if="productActivities.length > 1" class="other-activities"><label for="activity-select">选择活动</label><select id="activity-select" v-model="chosenActivityId"><option v-for="item in productActivities" :key="item.activityId" :value="item.activityId">{{ item.activityName }} · {{ statusText(item) }}</option></select></div>
      </section>
    </div>
  </div>
</template>
