<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from './api/apiClient'

const route = useRoute()
const isStorefront = computed(() => route.meta.surface === 'store')
const serviceState = ref<'checking' | 'online' | 'offline'>('checking')
const checkedAt = ref('')
const navGroups = [
  { label: '工作台', items: [{ title: '运营总览', path: '/dashboard', symbol: '总' }] },
  { label: '商品与活动', items: [{ title: '商品管理', path: '/products', symbol: '品' }, { title: '秒杀活动', path: '/activities', symbol: '活' }] },
  { label: '用户与权限', items: [{ title: '用户管理', path: '/users', symbol: '客' }] },
  { label: '系统运维', items: [{ title: '运行监控', path: '/operations', symbol: '监' }] },
]
const pageTitle = computed(() => String(route.meta.title ?? '运营总览'))
const adminTokenReady = Boolean(import.meta.env.VITE_ADMIN_TOKEN)
let healthTimer: ReturnType<typeof setInterval> | undefined

async function checkService() {
  try {
    await api.health()
    serviceState.value = 'online'
  } catch {
    serviceState.value = 'offline'
  }
  checkedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function showTokenHint() {
  ElMessage[adminTokenReady ? 'success' : 'warning'](adminTokenReady
    ? '管理令牌已配置。管理接口仍会由后端校验权限。'
    : '未检测到管理令牌，请在前端环境配置 VITE_ADMIN_TOKEN 后重启服务。')
}

onMounted(() => {
  void checkService()
  healthTimer = setInterval(() => void checkService(), 30000)
})
onBeforeUnmount(() => { if (healthTimer) clearInterval(healthTimer) })
</script>

<template>
  <RouterView v-if="isStorefront" />
  <el-container v-else class="admin-shell">
    <el-aside class="sidebar" width="248px">
      <RouterLink class="brand" to="/dashboard" aria-label="秒杀系统运营管理中心首页">
        <span class="brand-mark">闪</span>
        <span class="brand-copy"><strong>秒杀运营中心</strong><small>业务管理后台</small></span>
      </RouterLink>
      <div class="workspace-switch">
        <span class="workspace-avatar">电</span>
        <span class="workspace-copy"><strong>电商业务空间</strong><small>运营工作区</small></span>
        <span class="workspace-caret">⌄</span>
      </div>
      <nav class="side-navigation" aria-label="主导航">
        <section v-for="group in navGroups" :key="group.label" class="nav-group">
          <h2>{{ group.label }}</h2>
          <RouterLink v-for="item in group.items" :key="item.path" :to="item.path" class="nav-item" active-class="is-active">
            <span class="nav-symbol" aria-hidden="true">{{ item.symbol }}</span>
            <span>{{ item.title }}</span>
            <span v-if="route.path === item.path" class="nav-current" aria-hidden="true"></span>
          </RouterLink>
        </section>
      </nav>
      <div class="sidebar-foot">
        <div class="sidebar-foot-mark">i</div>
        <div><strong>接口能力说明</strong><p>后台数据来自当前服务接口</p></div>
      </div>
    </el-aside>

    <el-container class="main-column">
      <el-header class="top-header" height="68px">
        <div class="breadcrumb-line"><span class="breadcrumb-muted">管理中心</span><span class="breadcrumb-separator">/</span><strong>{{ pageTitle }}</strong></div>
        <div class="header-actions">
          <el-tooltip content="点击重新检查服务" placement="bottom">
            <button class="service-pill" type="button" @click="checkService">
              <span class="service-dot" :class="`state-${serviceState}`"></span>
              {{ serviceState === 'online' ? '服务正常' : serviceState === 'offline' ? '服务离线' : '检查中' }}
            </button>
          </el-tooltip>
          <span v-if="checkedAt" class="last-checked">最近检查 {{ checkedAt }}</span>
          <el-tooltip :content="adminTokenReady ? '管理令牌已注入前端请求' : '缺少 VITE_ADMIN_TOKEN 配置'" placement="bottom">
            <button class="token-indicator" type="button" @click="showTokenHint"><span class="token-symbol">钥</span>{{ adminTokenReady ? '管理鉴权已配置' : '管理令牌未配置' }}</button>
          </el-tooltip>
          <el-divider direction="vertical" />
          <el-avatar class="operator-avatar" :size="34">管</el-avatar>
          <div class="operator-copy"><strong>系统管理员</strong><small>运营管理</small></div>
        </div>
      </el-header>
      <el-main class="page-main"><RouterView /></el-main>
      <footer class="page-footer"><span>秒杀系统运营管理中心</span><span>业务数据以服务端接口为准</span></footer>
    </el-container>
  </el-container>
</template>
