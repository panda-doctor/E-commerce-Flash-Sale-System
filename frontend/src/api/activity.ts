import { http } from '../lib/http'
import { toId } from '../lib/id'
import type { ActivityDetail, ActivityItem } from '../types/activity'

/** 后端 13~15 位主键仍是 JSON number，入口处统一转字符串（见 lib/id.ts）。 */
function normalizeItem(item: ActivityItem): ActivityItem {
  return { ...item, activityId: toId(item.activityId), productId: toId(item.productId) }
}

function normalizeDetail(detail: ActivityDetail): ActivityDetail {
  return { ...detail, activityId: toId(detail.activityId), productId: toId(detail.productId) }
}

/** GET /api/seckill/activities → 活动列表（含商品名/图、预热标记、库存）。 */
export function fetchActivities() {
  return http
    .get<ActivityItem[]>('/api/seckill/activities')
    .then((list) => list.map(normalizeItem))
}

/** GET /api/seckill/activities/{activityId} → 活动详情。 */
export function fetchActivity(activityId: string) {
  return http
    .get<ActivityDetail>(`/api/seckill/activities/${encodeURIComponent(activityId)}`)
    .then(normalizeDetail)
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
