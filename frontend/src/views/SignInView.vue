<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/apiClient'
import { authState, bootstrapSession, establishSession } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const form = reactive({ accountName: '', password: '' })
const submitting = ref(false)
const accountError = ref('')
const passwordVisible = ref(false)
const redirectPath = computed(() => {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return target.startsWith('/') && !target.startsWith('//') ? target : '/'
})

onMounted(() => { void bootstrapSession() })

function validate(): boolean {
  accountError.value = ''
  if (!/^[a-z0-9._-]{3,32}$/i.test(form.accountName.trim())) {
    accountError.value = '请输入 3 至 32 位字母、数字或 . _ -。'
    return false
  }
  if (!form.password) {
    ElMessage.warning('请输入密码。')
    return false
  }
  return true
}

async function submit() {
  if (!validate() || submitting.value) return
  submitting.value = true
  try {
    const response = await api.login({ accountName: form.accountName.trim(), password: form.password })
    establishSession(response.user, response.csrfToken)
    ElMessage.success('欢迎回来。')
    await router.replace(redirectPath.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-shell">
      <aside class="auth-aside">
        <div class="auth-aside-orbit"></div><div class="auth-aside-orbit second"></div>
        <div class="auth-aside-content"><span class="auth-aside-label">闪购集市 · 认真挑选每一件好物</span><h1>好物不必<br>等很久。</h1><div class="auth-aside-art"><span class="auth-aside-spark">✦</span><div class="auth-aside-box">闪</div><span class="auth-aside-ring"></span></div></div>
      </aside>
      <div class="auth-panel">
        <RouterLink class="auth-back" to="/">返回商城</RouterLink>
        <div class="auth-panel-inner">
          <p class="auth-eyebrow">欢迎回来</p><h2>登录账号</h2><p class="auth-intro">登录后即可参与活动，查看你的订单。</p>
          <form class="auth-form" @submit.prevent="submit">
            <label for="signin-account">账号名</label>
            <input id="signin-account" v-model="form.accountName" autocomplete="username" maxlength="32" placeholder="请输入账号名" :aria-invalid="!!accountError" :aria-describedby="accountError ? 'signin-account-error' : undefined" @input="accountError = ''">
            <span v-if="accountError" id="signin-account-error" class="auth-field-error" role="alert">{{ accountError }}</span>
            <div class="auth-password-label"><label for="signin-password">密码</label><button type="button" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" :aria-pressed="passwordVisible" @click="passwordVisible = !passwordVisible">{{ passwordVisible ? '隐藏' : '显示' }}</button></div>
            <input id="signin-password" v-model="form.password" :type="passwordVisible ? 'text' : 'password'" autocomplete="current-password" maxlength="72" placeholder="请输入密码">
            <button class="auth-submit" type="submit" :disabled="submitting || authState.loading">{{ submitting ? '正在登录…' : '登录' }}</button>
          </form>
          <p class="auth-switch">还没有账号？<RouterLink to="/register">创建账号</RouterLink></p>
          <div class="auth-privacy">🔒 登录状态采用安全会话保护，不会在浏览器本地保存密码。</div>
        </div>
      </div>
    </div>
  </section>
</template>
