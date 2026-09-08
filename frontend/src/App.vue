<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from './api'
import { userStore } from './utils/store'
import { toasts } from './utils/toast'

const health = ref('checking') // checking | up | down
let healthTimer = null

const uidInput = ref(userStore.userId)

function applyUserId() {
  const v = Number(uidInput.value)
  if (Number.isInteger(v) && v > 0) userStore.userId = v
  else uidInput.value = userStore.userId
}

async function checkHealth() {
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
            <input
              v-model="uidInput"
              type="number"
              min="1"
              @change="applyUserId"
              @keydown.enter="applyUserId"
              aria-label="演示用户ID"
            />
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
.uid-box input {
  width: 88px;
  padding: 7px 12px;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  background: var(--surface);
  text-align: center;
  font-weight: 600;
  font-family: var(--font-num);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.uid-box input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-ring);
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
  .uid-box input {
    width: 72px;
  }
  .app-foot {
    flex-direction: column;
    align-items: flex-start;
    padding: 16px;
  }
}
</style>
