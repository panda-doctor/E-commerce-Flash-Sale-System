export interface ApiEnvelope<T> {
  code: number
  message: string
  data?: T
  requestId?: string
  timestamp?: number
}

export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly data?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export type ActivityStatus = 'NOT_STARTED' | 'RUNNING' | 'ENDED' | 'SOLD_OUT' | 'CANCELLED'
export type PreheatStatus = 'NOT_PREHEATED' | 'PREHEATED' | 'PREHEAT_FAILED'
export type ProductStatus = 'ON_SHELF' | 'OFF_SHELF'

export interface ActivityItem {
  activityId: string
  productId: string
  productName: string
  productImage?: string | null
  activityName: string
  startTime: string
  endTime: string
  seckillPrice: number
  originalPrice: number
  status: ActivityStatus
  stock: number
  preheated?: boolean | null
  totalStock: number
  limitPerUser: number
}

export interface ActivityDetail {
  activityId: string
  productId: string
  activityName: string
  startTime: string
  endTime: string
  status: ActivityStatus
  seckillPrice: number
  stock: number
  totalStock: number
  limitPerUser: number
}

export interface ActivityPayload {
  activityId?: string
  productId: string
  activityName: string
  startTime: string
  endTime: string
  seckillPrice: number
  seckillStock: number
  limitPerUser: 1
}

export interface ProductDetail {
  productId: string
  name: string
  description?: string | null
  imageUrl?: string | null
  originalPrice: number
  totalStock: number
  availableStock: number
  status: ProductStatus
  cacheHit: boolean
}

export interface ProductPayload {
  productId?: string
  name: string
  description?: string
  imageUrl?: string
  originalPrice: number
  totalStock: number
  status: ProductStatus
}

export interface ActivityMetrics {
  activityId: string
  redisStock: number | null
  orderCount: number
  queuedMessageCount: number
  successCount: number
  duplicateRejectCount: number
  rateLimitRejectCount: number
  soldOutRejectCount: number
}

export interface UploadResult {
  url: string
  storageType: string
}

export interface CreatedActivity {
  activityId: string
  status: ActivityStatus
}

export interface SavedProduct {
  productId: string
}

export interface PreheatResult {
  activityId: string
  activityKey: string
  stockKey: string
  stock: number
}

export interface StockResetResult {
  activityId: string
  stockKey: string
  stock: number
}

export interface DeadLetterReplayResult {
  originalMessageId: string
  replayMessageId: string
}

export interface UserProfile {
  id: string
  accountName: string
  displayName: string | null
  status: number
  createdAt: string
}

export interface AuthResponse {
  user: UserProfile
  csrfToken: string
}

export interface UserPage<T = UserProfile> {
  records: T[]
  page: number
  pageSize: number
  total: number
  pages: number
}

export interface SeckillOrderSummary {
  orderNo: string
  activityId: string
  productId: string
  status: 'QUEUING' | 'CREATED' | 'FAILED' | 'CANCELLED' | string
  createdAt?: string
}

export interface SeckillOrderStatus extends SeckillOrderSummary {
  statusDesc?: string
  userId?: string
  seckillPrice?: number
}
