<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/apiClient'
import { authState, bootstrapSession, clearSession } from '../stores/auth'
import ChatWidget from '../components/ChatWidget.vue'

const router = useRouter()
const route = useRoute()
const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')

watch(() => route.query.q, (value) => { searchText.value = typeof value === 'string' ? value : '' })

onMounted(() => { void bootstrapSession() })

async function signOut() {
  try {
    await api.logout()
    clearSession()
    ElMessage.success('已安全退出')
    await router.push('/')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出失败，请稍后重试')
  }
}

async function searchProducts() {
  await router.push({ name: 'shop-home', query: searchText.value.trim() ? { q: searchText.value.trim() } : {} })
  document.querySelector('#catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function goCatalog() {
  await router.push({ name: 'shop-home', hash: '#catalog' })
  document.querySelector('#catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="storefront" :class="{ 'storefront-product-detail': route.name === 'product-detail', 'storefront-catalog': route.name === 'shop-home' || route.name === 'flash-sale' }">
    <header class="store-header">
      <div class="store-container store-header-inner">
        <RouterLink class="store-brand" to="/" aria-label="闪购集市首页"><span class="store-brand-mark">闪</span><span><strong>闪购集市</strong><small>商品 · 秒杀活动</small></span></RouterLink>
        <nav class="store-nav" aria-label="商城导航">
          <RouterLink to="/" :class="{ active: route.path === '/' && !route.hash }"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m3.5 10 8.5-7 8.5 7v9a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"/><path d="M9 21v-7h6v7"/></svg><span>首页</span></RouterLink>
          <a href="/#catalog" :class="{ active: route.hash === '#catalog' }" @click.prevent="goCatalog"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/></svg><span>全部商品</span></a>
          <RouterLink to="/flash-sale" active-class="active"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m13.5 2.8-8 11h6l-1 7.4 8-11h-6z"/></svg><span>限时秒杀</span></RouterLink>
          <RouterLink to="/account/orders" active-class="active"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h12v17l-2.5-1.7-3.5 1.7-3.5-1.7L6 20.5z"/><path d="M9 8h6M9 12h6M9 16h3"/></svg><span>我的订单</span></RouterLink>
        </nav>
        <form v-if="route.name !== 'shop-home' && route.name !== 'flash-sale'" class="store-global-search" role="search" @submit.prevent="searchProducts"><label class="sr-only" for="store-search">搜索商品</label><span aria-hidden="true">⌕</span><input id="store-search" v-model="searchText" type="search" placeholder="搜索商品名称" autocomplete="off"><button type="submit" aria-label="提交搜索">搜索</button></form>
        <div class="store-account">
          <template v-if="authState.user">
            <span class="account-divider"></span>
            <span class="account-greeting">你好，{{ authState.user.displayName || authState.user.accountName }}</span>
            <button class="account-logout" type="button" @click="signOut">退出</button>
          </template>
          <template v-else>
            <RouterLink class="store-login" to="/sign-in">登录</RouterLink>
            <RouterLink class="store-register" to="/register">免费注册</RouterLink>
          </template>
        </div>
      </div>
    </header>
    <nav class="store-bottom-nav" aria-label="商城底部导航">
      <RouterLink to="/" :class="{ active: route.path === '/' && !route.hash }"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m3.5 10 8.5-7 8.5 7v9a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"/><path d="M9 21v-7h6v7"/></svg><span>首页</span></RouterLink>
      <a href="/#catalog" :class="{ active: route.hash === '#catalog' }" @click.prevent="goCatalog"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/></svg><span>全部商品</span></a>
      <RouterLink to="/flash-sale" active-class="active"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m13.5 2.8-8 11h6l-1 7.4 8-11h-6z"/></svg><span>限时秒杀</span></RouterLink>
      <RouterLink to="/account/orders" active-class="active"><svg class="store-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h12v17l-2.5-1.7-3.5 1.7-3.5-1.7L6 20.5z"/><path d="M9 8h6M9 12h6M9 16h3"/></svg><span>我的订单</span></RouterLink>
    </nav>
    <main class="store-main"><RouterView /></main>
    <footer class="store-footer"><div class="store-container store-footer-inner"><small>© 2026 闪购集市</small></div></footer>
    <ChatWidget />
  </div>
</template>
