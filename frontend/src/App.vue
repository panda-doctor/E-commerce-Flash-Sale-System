<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from './api'
import { accountOf, DEMO_ACCOUNTS, localTokenOf, rememberToken, userStore } from './utils/store'
import { toasts, toastErr, toastOK, toastWarn } from './utils/toast'

const health = ref('checking') // checking | up | down
let healthTimer = null

// ---------- 演示用户账号切换 ----------
// 下拉 = 静态演示账号（切换即生效）；「自定义用户 ID」= 动态注册用户：
//   输入未占用 id 回车后点「领取令牌」（POST /api/auth/register，服务端随机令牌 + 内存登记），
//   令牌存 localStorage，execute / check / 查单即可通过 R3 鉴权；
//   后端重启会清空内存注册表、旧令牌失效，届时点「已注册」按钮可重新领取刷新。
const CUSTOM_KEY = '__custom__'
const uidInput = ref(userStore.userId)
const uidInputEl = ref(null)
const currentAccount = computed(() => accountOf(userStore.userId))
const customMode = ref(currentAccount.value == null)
const selectValue = computed({
  get: () => (customMode.value ? CUSTOM_KEY : userStore.userId),
  set: (v) => {
    if (v === CUSTOM_KEY) {
      customMode.value = true
      uidInput.value = ''
      nextTick(() => uidInputEl.value && uidInputEl.value.focus())
      return
    }
    customMode.value = false
    if (v) userStore.userId = String(v)
  },
})

// 动态用户（非静态账号）本地是否已领到令牌：决定顶栏显示「领取令牌」还是「已注册」
const localHasToken = computed(
  () => customMode.value && !currentAccount.value && !!localTokenOf(userStore.userId),
)
const claiming = ref(false)
const claimText = computed(() =>
  claiming.value ? '领取中…' : localHasToken.value ? '✓ 已注册' : '领取令牌',
)
const claimTitle = computed(() =>
  claiming.value
    ? '正在向服务端领取动态令牌…'
    : localHasToken.value
      ? '该用户已领取动态令牌（保存在本地）。后端重启会清空内存注册表、旧令牌随之失效，点击可重新领取刷新'
      : '为该用户 ID 注册服务端随机动态令牌（演示环境内存登记），注册后 execute / 活动校验 / 查单即可通过鉴权',
)

async function claimToken() {
  const typed = String(uidInput.value).trim()
  if (typed && typed !== String(userStore.userId)) applyUserId() // 输入了新 id 但未回车确认，先应用
  const uid = String(userStore.userId)
  if (!/^[1-9]\d{0,18}$/.test(uid)) {
    toastWarn('请输入合法的用户 ID（1-9 开头的数字）后再领取')
    return
  }
  if (accountOf(uid)) {
    toastWarn('静态演示账号无需领取令牌，请在下拉中直接选择')
    return
  }
  claiming.value = true
  try {
    const data = await api.registerUser(uid) // userId 字符串透传，后端 Long 接收
    rememberToken(uid, data.token)
    toastOK(`用户 #${uid} 注册成功，动态令牌已生效`)
  } catch (e) {
    if (e.code === 40903) {
      toastWarn(
        localTokenOf(uid)
          ? `用户 #${uid} 仍处于已注册状态且令牌有效，可直接参与抢购`
          : `用户 ID #${uid} 已被占用（静态账号或他人已注册），请更换后再领取`,
      )
    } else {
      toastErr(e.message || '领取令牌失败')
    }
  } finally {
    claiming.value = false
  }
}

function applyUserId() {
  const v = String(uidInput.value).trim()
  if (!/^[1-9]\d{0,18}$/.test(v)) {
    uidInput.value = userStore.userId
    return
  }
  userStore.userId = v
  if (accountOf(v)) customMode.value = false // 恰为已配置账号，回到下拉选中态
}

async function checkHealth() {
  if (document.hidden) return
  try {
    await api.health()
    health.value = 'up'
  } catch (e) {
    health.value = 'down'
  }
}

onMounted(() => {
  checkHealth()
  healthTimer = setInterval(checkHealth, 5000)
})
onBeforeUnmount(() => clearInterval(healthTimer))
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink to="/" class="brand">
          <span class="brand-mark">
            <span class="brand-bolt">⚡</span>
          </span>
          <span class="brand-text">
            <span class="brand-name">闪电秒杀</span>
            <span class="brand-sub">LIVE DASHBOARD</span>
          </span>
        </RouterLink>

        <nav class="nav">
          <RouterLink to="/" active-class="active">活动广场</RouterLink>
          <RouterLink to="/ai-service" active-class="active">AI 客服</RouterLink>
          <RouterLink to="/admin" active-class="active">管理控制台</RouterLink>
        </nav>

        <div class="top-right">
          <span class="health" :class="`health-${health}`" :title="'后端状态：' + health">
            <span class="health-dot"></span>
            <span class="health-text">{{ health === 'up' ? '服务正常' : health === 'down' ? '服务离线' : '检测中' }}</span>
          </span>
          <label class="uid-box">
            <span class="uid-label">演示用户</span>
            <select
              v-if="!customMode"
              v-model="selectValue"
              class="uid-select"
              aria-label="演示用户账号"
            >
              <option v-for="a in DEMO_ACCOUNTS" :key="a.userId" :value="a.userId">
                {{ a.name }} · #{{ a.userId }}
              </option>
              <option :value="CUSTOM_KEY">自定义用户 ID…</option>
            </select>
            <input
              v-else
              ref="uidInputEl"
              v-model="uidInput"
              type="number"
              min="1"
              placeholder="输入 userId"
              @change="applyUserId"
              @keydown.enter="applyUserId"
              aria-label="演示用户自定义ID"
            />
            <button
              v-if="customMode && !currentAccount"
              class="uid-claim"
              :class="{ 'is-done': localHasToken }"
              :disabled="claiming"
              :title="claimTitle"
              @click="claimToken"
            >{{ claimText }}</button>
          </label>
        </div>

        <button class="menu-toggle" aria-label="菜单">
          <span></span><span></span><span></span>
        </button>
      </div>
    </header>

    <main class="content">
      <RouterView />
    </main>

    <footer class="app-foot">
      <span>闪电秒杀 · 轻量级电商秒杀系统实时看板</span>
      <span class="foot-dot">Lua 原子防超卖 · 消息削峰异步建单 · 限购防重</span>
    </footer>

    <Teleport to="body">
      <TransitionGroup name="toast" tag="div" class="toast-stack">
        <div v-for="t in toasts" :key="t.id" class="toast" :class="`toast-${t.type}`">
          <span class="toast-ico">{{ t.type === 'success' ? '✓' : t.type === 'error' ? '✕' : t.type === 'warning' ? '!' : 'i' }}</span>
          <span class="toast-msg">{{ t.message }}</span>
        </div>
      </TransitionGroup>
    </Teleport>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px) saturate(1.4);
  -webkit-backdrop-filter: blur(14px) saturate(1.4);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.6) inset, 0 2px 12px rgba(0, 0, 0, 0.04);
}
.topbar-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 12px 28px;
}

/* ---------- 品牌 ---------- */
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
}
.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--grad-cta);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px -4px rgba(255, 45, 85, 0.5);
  position: relative;
  overflow: hidden;
}
.brand-mark::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(150deg, rgba(255, 255, 255, 0.35), transparent 50%);
}
.brand-bolt {
  font-size: 22px;
  color: #fff;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.2));
  position: relative;
  z-index: 1;
}
.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}
.brand-name {
  font-weight: 800;
  font-size: 19px;
  letter-spacing: 0.5px;
  color: var(--text);
}
.brand-sub {
  color: var(--text-3);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1.5px;
  margin-top: 2px;
}

/* ---------- 导航 ---------- */
.nav {
  display: flex;
  gap: 4px;
  margin-left: 8px;
  background: var(--surface-2);
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--border);
}
.nav a {
  padding: 8px 18px;
  border-radius: 8px;
  color: var(--text-2);
  font-size: 14px;
  font-weight: 600;
  transition: all 0.18s var(--ease-out);
}
.nav a:hover {
  color: var(--text);
}
.nav a.active {
  background: var(--surface);
  color: var(--accent);
  box-shadow: var(--shadow-1);
}

/* ---------- 右侧 ---------- */
.top-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}

.health {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--surface-2);
  border: 1px solid var(--border);
}
.health-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.health-up .health-dot {
  background: var(--success);
  box-shadow: 0 0 0 3px var(--success-soft);
  animation: pulseDot 2s ease infinite;
}
.health-down .health-dot {
  background: var(--danger);
  box-shadow: 0 0 0 3px var(--danger-soft);
}
.health-checking .health-dot {
  background: var(--warning);
  box-shadow: 0 0 0 3px var(--warning-soft);
}
.health-up .health-text { color: var(--success); }
.health-down .health-text { color: var(--danger); }
.health-checking .health-text { color: var(--warning); }
@keyframes pulseDot {
  50% { opacity: 0.5; }
}

.uid-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.uid-label {
  font-size: 13px;
  color: var(--text-2);
  font-weight: 600;
}
.uid-box .uid-select,
.uid-box input {
  padding: 7px 12px;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  background: var(--surface);
  font-weight: 600;
  font-size: 13px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.uid-box .uid-select {
  min-width: 168px;
  max-width: 210px;
  cursor: pointer;
  color: var(--text);
}
.uid-box .uid-select:focus-visible,
.uid-box input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-ring);
}
.uid-box input {
  width: 92px;
  text-align: center;
  font-family: var(--font-num);
}
.uid-claim {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: 999px;
  background: var(--grad-cta);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 4px 10px -4px rgba(255, 45, 85, 0.5);
  transition: filter 0.15s, transform 0.15s, opacity 0.15s;
}
.uid-claim:hover:not(:disabled) {
  filter: brightness(1.08);
}
.uid-claim:active:not(:disabled) {
  transform: scale(0.96);
}
.uid-claim:disabled {
  opacity: 0.65;
  cursor: default;
}
.uid-claim.is-done {
  background: var(--success);
  box-shadow: 0 4px 10px -4px rgba(52, 199, 89, 0.5);
}

.menu-toggle {
  display: none;
  flex-direction: column;
  gap: 5px;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--surface-2);
  border: 1px solid var(--border);
}
.menu-toggle span {
  width: 18px;
  height: 2px;
  background: var(--text);
  border-radius: 2px;
}

.content {
  flex: 1;
  width: 100%;
}

/* ---------- 页脚 ---------- */
.app-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 20px 28px 28px;
  max-width: 1280px;
  margin: 0 auto;
  width: 100%;
  font-size: 13px;
  color: var(--text-3);
  border-top: 1px solid var(--border);
  margin-top: 32px;
}
.foot-dot {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* ---------- toast ---------- */
.toast-stack {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}
.toast {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 280px;
  max-width: 400px;
  padding: 14px 18px;
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-3);
  border: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
}
.toast-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  flex: none;
}
.toast-msg {
  flex: 1;
}
.toast-success {
  border-color: rgba(52, 199, 89, 0.3);
}
.toast-success .toast-ico {
  background: var(--success);
}
.toast-error {
  border-color: rgba(220, 38, 38, 0.35);
}
.toast-error .toast-ico {
  background: var(--danger);
}
.toast-warning {
  border-color: rgba(245, 158, 11, 0.4);
}
.toast-warning .toast-ico {
  background: var(--warning);
}
.toast-info {
  border-color: rgba(37, 99, 235, 0.3);
}
.toast-info .toast-ico {
  background: var(--info);
}

.toast-enter-active,
.toast-leave-active {
  transition: transform 0.26s var(--ease-spring), opacity 0.22s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(28px) scale(0.95);
}

/* ---------- 响应式 ---------- */
@media (max-width: 760px) {
  .topbar-inner {
    padding: 10px 16px;
    gap: 10px;
  }
  .brand-text {
    display: none;
  }
  .nav {
    display: none;
  }
  .uid-label {
    display: none;
  }
  .health-text {
    display: none;
  }
  .health {
    padding: 6px;
  }
  .uid-box .uid-select {
    min-width: 110px;
    max-width: 150px;
  }
  .uid-box input {
    width: 72px;
  }
  .uid-claim {
    display: none;
  }
  .app-foot {
    flex-direction: column;
    align-items: flex-start;
    padding: 16px;
  }
}
</style>
