import { http } from '../lib/http'
import type { SeckillExecuteResult } from '../types/activity'

/**
 * POST /api/seckill/execute
 * 请求体只有 `{ activityId, productId? }`（秒杀语义固定一件，后端无 quantity；
 * `userId` 由后端从会话取并覆盖入参，前端不得传）。
 */
export function executeSeckill(activityId: string, productId?: string) {
  const body: Record<string, string> = { activityId }
  if (productId) {
    body.productId = productId
  }
  return http.post<SeckillExecuteResult>('/api/seckill/execute', body)
}
