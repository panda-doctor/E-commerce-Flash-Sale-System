import { reactive, watch } from 'vue'

// =====================================================
// 演示账号静态表（顶栏下拉渲染来源）
//   与后端 E-commerceFlashSaleSystem/.env 的 USER_TOKENS 保持 userId/token 一致（六账号）。
// 新增演示用户已无需改本表：下拉切「自定义用户 ID」→ 输入未占用 id → 点「领取令牌」，
//   后端 POST /api/auth/register 动态登记并返回随机令牌，令牌保存在本文件下方的本地缓存。
// http.js 按 userStore.userId 携带令牌：静态表 → 本地动态缓存 → env 兜底。
// =====================================================
export const DEMO_ACCOUNTS = [
  { userId: '1001', token: 'user-a', name: '演示用户 A' },
  { userId: '1002', token: 'user-b', name: '演示用户 B' },
  { userId: '1003', token: 'user-c', name: '演示用户 C' },
  { userId: '1004', token: 'user-d', name: '演示用户 D' },
  { userId: '1005', token: 'user-e', name: '演示用户 E' },
  { userId: '1006', token: 'user-f', name: '演示用户 F' },
]

/** 按 userId（字符串/数字均可）查静态账号，未配置返回 null */
export function accountOf(userId) {
  const key = String(userId)
  return DEMO_ACCOUNTS.find((a) => a.userId === key) || null
}

// ---------------- 动态发令牌：本地已领令牌缓存（localStorage） ----------------
// 顶栏「领取令牌」成功后写入；后端重启会清空内存注册表、旧令牌随之失效，
// 届时顶栏「已注册」标签可点击重新领取刷新（见 App.vue claimToken）。
const TOKEN_CACHE_KEY = 'flash_demo_user_tokens'

function readTokenCache() {
  try {
    return JSON.parse(localStorage.getItem(TOKEN_CACHE_KEY) || '{}')
  } catch {
    return {}
  }
}

/** 读取本地缓存中该 userId 的动态令牌；未领取返回 null */
export function localTokenOf(userId) {
  const cache = readTokenCache()
  return cache[String(userId)] || null
}

/** 领取成功后持久化 uid -> token（覆盖旧值，用于后端重启后刷新） */
export function rememberToken(userId, token) {
  const cache = readTokenCache()
  cache[String(userId)] = token
  localStorage.setItem(TOKEN_CACHE_KEY, JSON.stringify(cache))
}

/** 按 userId 取其访问令牌：静态账号优先，未命中查本地动态缓存；均无返回 null */
export function tokenOf(userId) {
  const account = accountOf(userId)
  return account ? account.token : localTokenOf(userId)
}

// 轻量全局状态：演示用户身份（项目无用户系统，userId 由前端模拟并透传）
export const userStore = reactive({
  // 标识在前端始终作为字符串透传，避免后端 Long 超过 JS 安全整数后发生静默取整。
  userId: localStorage.getItem('flash_demo_user_id') || '1001',
})

watch(
  () => userStore.userId,
  (v) => localStorage.setItem('flash_demo_user_id', String(v)),
)

export const uiState = reactive({
  // 当前演示的活动 id（两个页面可联动）
  activityId: localStorage.getItem('flash_demo_activity_id') || null,
  setActivity(id) {
    uiState.activityId = id == null ? null : String(id)
    if (uiState.activityId == null) localStorage.removeItem('flash_demo_activity_id')
    else localStorage.setItem('flash_demo_activity_id', uiState.activityId)
  },
})
