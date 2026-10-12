<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/apiClient'
import type { ActivityItem, SeckillOrderSummary } from '../types/api'
import { authState, bootstrapSession } from '../stores/auth'
import ProductImage from '../components/ProductImage.vue'

const router = useRouter()
const orders = ref<SeckillOrderSummary[]>([])
const activities = ref<ActivityItem[]>([])
const loading = ref(true)
const error = ref('')
const statusNotice = ref('')
const checkedAt = ref('')
const activityById = computed(() => new Map(activities.value.map(activity => [activity.activityId, activity])))
let pollTimer: ReturnType<typeof setInterval> | undefined
let polling = false

onMounted(async () => {
  await loadOrders()
  if (authState.user) pollTimer = setInterval(() => { if (!document.hidden) void pollPendingOrders() }, 3000)
})
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })

async function loadOrders() {
  await bootstrapSession()
  if (!authState.user) {
    await router.replace({ name: 'sign-in', query: { redirect: '/account/orders' } })
    return
  }
  loading.value = true
  error.value = ''
  try {
    orders.value = await api.listMyOrders()
    try { activities.value = await api.listActivities() } catch { activities.value = [] }
    statusNotice.value = ''
    checkedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '订单暂时无法加载。'
  } finally {
    loading.value = false
  }
}

async function pollPendingOrders() {
  if (polling) return
  const pending = orders.value.filter(order => order.status === 'QUEUING' || order.status === 'PENDING')
  if (!pending.length) return
  polling = true
  let unavailable = false
  try {
    await Promise.all(pending.map(async (order) => {
      try {
        const latest = await api.getOrderStatus(order.orderNo)
        order.status = latest.status
      } catch {
        unavailable = true
      }
    }))
    statusNotice.value = unavailable ? '部分处理中订单的状态暂时无法更新，页面没有将其判定为失败。' : ''
    checkedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } finally {
    polling = false
  }
}

function statusTitle(status: string): string {
  return ({ QUEUING: '正在处理', PENDING: '正在处理', CREATED: '已创建', FAILED: '处理失败', CANCELLED: '已取消' } as Record<string, string>)[status] ?? status
}

function statusClass(status: string): string {
  if (status === 'CREATED') return 'order-created'
  if (status === 'FAILED' || status === 'CANCELLED') return 'order-failed'
  return 'order-pending'
}

async function viewOrder(order: SeckillOrderSummary) {
  try {
    const latest = await api.getOrderStatus(order.orderNo)
    order.status = latest.status
    ElMessage.info(`订单状态：${statusTitle(latest.status)}${latest.statusDesc ? ` · ${latest.statusDesc}` : ''}`)
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '暂时无法读取订单状态。')
  }
}
</script>

<template>
  <section class="shop-container orders-page">
    <div class="detail-breadcrumb"><RouterLink to="/">首页</RouterLink><span class="breadcrumb-divider" aria-hidden="true">›</span><strong>我的订单</strong></div>
    <div class="orders-heading"><div><h1>我的订单</h1><p>查看秒杀订单的处理状态。</p></div><button class="refresh-button" type="button" :disabled="loading" @click="loadOrders"><span :class="{ spinning: loading }">↻</span> 刷新订单</button></div>
    <div class="orders-summary"><div><span>订单数量</span><strong>{{ orders.length }} <small>笔</small></strong></div><div><span>最近更新</span><strong>{{ checkedAt || '—' }}</strong></div></div>
    <p v-if="statusNotice" class="order-status-notice" role="status">{{ statusNotice }}</p>
    <div v-if="error" class="shop-state orders-state"><div class="state-icon">!</div><h3>订单暂时没有加载出来</h3><p>{{ error }}</p><button class="solid-button" type="button" @click="loadOrders">重新加载</button></div>
    <div v-else-if="loading" class="orders-loading"><span></span><span></span><span></span></div>
    <div v-else-if="orders.length" class="orders-list">
      <article v-for="order in orders" :key="order.orderNo" class="order-card">
        <div class="order-card-head"><div><span class="order-label">订单编号</span><strong>{{ order.orderNo }}</strong></div><span class="order-status" :class="statusClass(order.status)"><i></i>{{ statusTitle(order.status) }}</span></div>
        <div class="order-card-content"><div class="order-product-mark"><ProductImage :src="activityById.get(order.activityId)?.productImage" :alt="activityById.get(order.activityId)?.productName || '秒杀商品'" variant="order" /></div><div class="order-product-copy"><strong>{{ activityById.get(order.activityId)?.productName || '秒杀商品' }}</strong><span>{{ activityById.get(order.activityId)?.activityName || `活动编号 ${order.activityId}` }}</span><span>商品编号 {{ order.productId }} · 活动价 {{ activityById.get(order.activityId) ? `¥${(activityById.get(order.activityId)!.seckillPrice / 100).toFixed(2)}` : '订单接口暂未提供' }}</span></div><div class="order-created-at"><span>提交时间</span><strong>{{ order.createdAt || '正在确认' }}</strong></div></div>
        <div class="order-card-foot"><p v-if="order.status === 'QUEUING' || order.status === 'PENDING'">请求已提交，稍后可刷新查询进度。</p><p v-else-if="order.status === 'CREATED'">请保留订单编号，后续可查询状态。</p><p v-else>如需协助，请联系商城客服。</p><div><button class="order-check-button" type="button" @click="viewOrder(order)">查询最新状态</button><RouterLink class="story-link" :to="`/products/${encodeURIComponent(order.productId)}?activity=${encodeURIComponent(order.activityId)}`">查看活动</RouterLink></div></div>
      </article>
    </div>
    <div v-else class="orders-empty"><span class="empty-order-icon">▤</span><h2>还没有订单</h2><p>参与一场喜欢的秒杀活动，订单记录会显示在这里。</p><RouterLink to="/" class="solid-button empty-shop-link">去逛逛好物</RouterLink></div>
  </section>
</template>
