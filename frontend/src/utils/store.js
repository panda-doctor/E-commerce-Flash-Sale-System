import { reactive, watch } from 'vue'

// 轻量全局状态：演示用户身份（项目无用户系统，userId 由前端模拟并透传）
export const userStore = reactive({
  userId: Number(localStorage.getItem('flash_demo_user_id')) || 1001,
})

watch(
  () => userStore.userId,
  (v) => localStorage.setItem('flash_demo_user_id', String(v)),
)

export const uiState = reactive({
  // 当前演示的活动 id（两个页面可联动）
  activityId: Number(localStorage.getItem('flash_demo_activity_id')) || null,
  setActivity(id) {
    uiState.activityId = id
    localStorage.setItem('flash_demo_activity_id', String(id))
  },
})
