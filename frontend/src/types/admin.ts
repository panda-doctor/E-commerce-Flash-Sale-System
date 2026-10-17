import type { Id } from './api'
import type { ActivityStatus } from './activity'
import type { Product, ProductStatus } from './product'

/** `ActivityMetricsVO`：管理端实时指标。 */
export interface ActivityMetrics {
  activityId: Id
  redisStock: number | null
  orderCount: number | null
  queuedMessageCount: number | null
  successCount: number | null
  duplicateRejectCount: number | null
  rateLimitRejectCount: number | null
  soldOutRejectCount: number | null
}

/** `ProductRequest`：管理端商品新建/更新。 */
export interface ProductUpsertPayload {
  productId?: Id
  name: string
  description?: string
  imageUrl?: string
  originalPrice: number
  totalStock: number
  status?: ProductStatus
}

/** `ActivityRequest`：管理端活动新建。 */
export interface ActivityCreatePayload {
  activityId?: Id
  productId: Id
  activityName: string
  startTime: string
  endTime: string
  seckillPrice: number
  seckillStock: number
  limitPerUser?: number
}

export interface UploadedImage {
  url: string
  storageType?: string
}

export interface CreateResult {
  activityId?: Id
  productId?: Id
  [key: string]: unknown
}

export type { ActivityStatus, Product }
