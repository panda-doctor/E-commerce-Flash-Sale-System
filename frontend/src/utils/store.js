import { reactive, watch } from 'vue'

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
