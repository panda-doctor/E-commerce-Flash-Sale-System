import { http } from '../lib/http'
import type {
  ActivityCreatePayload,
  ActivityMetrics,
  CreateResult,
  ProductUpsertPayload,
  UploadedImage,
} from '../types/admin'
import type { Product } from '../types/product'

/** 管理端请求统一带 `X-Admin-Token`。 */
const withToken = (token: string) => ({ adminToken: token })

/** POST /api/admin/products → 新建/更新商品（ProductRequest）。 */
export function saveProduct(token: string, payload: ProductUpsertPayload) {
  return http.post<CreateResult>('/api/admin/products', payload, withToken(token))
}

/** GET /api/admin/products/{id} */
export function fetchAdminProduct(token: string, productId: string) {
  return http.get<Product>(`/api/admin/products/${encodeURIComponent(productId)}`, withToken(token))
}

/** POST /api/admin/files/image（multipart 字段名必须是 file）。 */
export function uploadImage(token: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.upload<UploadedImage>('/api/admin/files/image', formData, withToken(token))
}

/** POST /api/admin/seckill/activities → 创建活动（ActivityRequest）。 */
export function createActivity(token: string, payload: ActivityCreatePayload) {
  return http.post<CreateResult>('/api/admin/seckill/activities', payload, withToken(token))
}

/** POST /api/admin/seckill/activities/{id}/preheat → 预热库存到 Redis。 */
export function preheatActivity(token: string, activityId: string) {
  return http.post<CreateResult>(
    `/api/admin/seckill/activities/${encodeURIComponent(activityId)}/preheat`,
    undefined,
    withToken(token),
  )
}

/** POST /api/admin/seckill/activities/{id}/reset-stock → 重置库存。 */
export function resetActivityStock(token: string, activityId: string) {
  return http.post<CreateResult>(
    `/api/admin/seckill/activities/${encodeURIComponent(activityId)}/reset-stock`,
    undefined,
    withToken(token),
  )
}

/** GET /api/admin/seckill/activities/{id}/metrics → 实时指标。 */
export function fetchMetrics(token: string, activityId: string) {
  return http.get<ActivityMetrics>(
    `/api/admin/seckill/activities/${encodeURIComponent(activityId)}/metrics`,
    withToken(token),
  )
}

/** POST /api/admin/seckill/activities/{id}/snapshot → 指标快照落库。 */
export function captureSnapshot(token: string, activityId: string) {
  return http.post<null>(
    `/api/admin/seckill/activities/${encodeURIComponent(activityId)}/snapshot`,
    undefined,
    withToken(token),
  )
}

/** POST /api/admin/seckill/dead-letters/replay-batch → 批量重放死信。 */
export function replayDeadLetters(token: string, originalMessageIds: string[]) {
  return http.post<Record<string, unknown>>(
    '/api/admin/seckill/dead-letters/replay-batch',
    { originalMessageIds },
    withToken(token),
  )
}
