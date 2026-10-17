import { http } from '../lib/http'
import type { ActivityDetail, ActivityItem } from '../types/activity'

/** GET /api/seckill/activities → 活动列表（含商品名/图、预热标记、库存）。 */
export function fetchActivities() {
  return http.get<ActivityItem[]>('/api/seckill/activities')
}

/** GET /api/seckill/activities/{activityId} → 活动详情。 */
export function fetchActivity(activityId: string) {
  return http.get<ActivityDetail>(`/api/seckill/activities/${encodeURIComponent(activityId)}`)
}

/**
 * GET /api/seckill/activities/{activityId}/check → 参与资格校验。
 * 后端返回结构未在 DTO 中固定（Map），因此按「可参与 = code 0」使用，不臆造字段。
 */
export function checkEligibility(activityId: string) {
  return http.get<Record<string, unknown> | null>(
    `/api/seckill/activities/${encodeURIComponent(activityId)}/check`,
  )
}
