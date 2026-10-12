<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/apiClient'
import { bootstrapSession, establishSession } from '../stores/auth'

const router = useRouter()
const form = reactive({ accountName: '', displayName: '', password: '', confirmPassword: '' })
const submitting = ref(false)
const passwordVisible = ref(false)
const accountError = ref('')

onMounted(() => { void bootstrapSession() })

function validate(): boolean {
  accountError.value = ''
  form.accountName = form.accountName.trim()
  if (!/^[a-z0-9._-]{3,32}$/i.test(form.accountName)) {
    accountError.value = '账号名须为 3 至 32 位字母、数字或 . _ -。'
    return false
  }
  if (form.displayName.trim().length > 64) {
    ElMessage.warning('昵称最多 64 个字符。')
    return false
  }
  if (form.password.length < 10 || new TextEncoder().encode(form.password).length > 72) {
    ElMessage.warning('密码至少 10 个字符，且 UTF-8 长度不能超过 72 字节。')
    return false
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致。')
    return false
  }
  return true
}

async function submit() {
  if (!validate() || submitting.value) return
  submitting.value = true
  try {
    const response = await api.register({
      accountName: form.accountName,
      password: form.password,
      ...(form.displayName.trim() ? { displayName: form.displayName.trim() } : {}),
    })
    establishSession(response.user, response.csrfToken)
    ElMessage.success('账号创建成功，欢迎加入。')
    await router.replace('/')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-shell">
      <aside class="auth-aside register-aside">
        <div class="auth-aside-orbit"></div><div class="auth-aside-orbit second"></div>
        <div class="auth-aside-content"><span class="auth-aside-label">闪购集市 · 为喜欢的生活添一点惊喜</span><h1>从发现好物，<br>到安心拥有。</h1><div class="register-note"><span>01</span><div><strong>只收集必要信息</strong><small>使用账号名登录，不收集手机号或邮箱</small></div></div></div>
      </aside>
      <div class="auth-panel">
        <RouterLink class="auth-back" to="/">返回商城</RouterLink>
        <div class="auth-panel-inner register-panel-inner">
          <p class="auth-eyebrow">开启你的好物之旅</p><h2>创建账号</h2><p class="auth-intro">填写以下信息即可开始参与限时活动。</p>
          <form class="auth-form" @submit.prevent="submit">
            <label for="signup-account">账号名 <small>3 至 32 位</small></label>
            <input id="signup-account" v-model="form.accountName" autocomplete="username" maxlength="32" placeholder="字母、数字或 . _ -" :aria-invalid="!!accountError" :aria-describedby="accountError ? 'signup-account-error' : undefined" @input="accountError = ''">
            <span v-if="accountError" id="signup-account-error" class="auth-field-error" role="alert">{{ accountError }}</span>
            <label for="signup-name">昵称 <small>选填</small></label>
            <input id="signup-name" v-model="form.displayName" autocomplete="nickname" maxlength="64" placeholder="你希望别人如何称呼你">
            <div class="auth-password-label"><label for="signup-password">设置密码 <small>至少 10 个字符</small></label><button type="button" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" :aria-pressed="passwordVisible" @click="passwordVisible = !passwordVisible">{{ passwordVisible ? '隐藏' : '显示' }}</button></div>
            <input id="signup-password" v-model="form.password" :type="passwordVisible ? 'text' : 'password'" autocomplete="new-password" maxlength="72" placeholder="请设置登录密码">
            <label for="signup-confirm">确认密码</label>
            <input id="signup-confirm" v-model="form.confirmPassword" :type="passwordVisible ? 'text' : 'password'" autocomplete="new-password" maxlength="72" placeholder="请再次输入密码">
            <button class="auth-submit" type="submit" :disabled="submitting">{{ submitting ? '正在创建…' : '创建账号并继续' }}</button>
          </form>
          <p class="auth-switch">已经有账号？<RouterLink to="/sign-in">立即登录</RouterLink></p>
          <div class="auth-privacy">🔒 密码只以单向哈希保存，登录状态由安全会话保护。</div>
        </div>
      </div>
    </div>
  </section>
</template>
