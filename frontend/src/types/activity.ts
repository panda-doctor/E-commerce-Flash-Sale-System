import type { Id } from './api'

/**
 * `ActivityStatusEnum`（已按真实代码核对）：
 * `NOT_STARTED(0) / RUNNING(1) / ENDED(2) / SOLD_OUT(3) / CANCELLED(4)`。
 * 注意：真实值是 `RUNNING`，不是 `IN_PROGRESS`。
 */
export type ActivityStatus =
  | 'NOT_STARTED'
  | 'RUNNING'
  | 'ENDED'
  | 'SOLD_OUT'
  | 'CANCELLED'
  | string

/** `ActivityItemVO`：列表项（含商品名/图与预热标记）。 */
export interface ActivityItem {
  activityId: Id
  productId: Id
  productName: string | null
  productImage: string | null
  activityName: string | null
  startTime: string
  endTime: string
  seckillPrice: number | null
  originalPrice: number | null
  status: ActivityStatus
  stock: number | null
  preheated: boolean | null
  totalStock: number | null
  limitPerUser: number | null
}

/** `SeckillActivityVO`：详情（无 preheated 字段，`stock === null` 即未预热）。 */
export interface ActivityDetail {
  activityId: Id
  productId: Id
  activityName: string | null
  startTime: string
  endTime: string
  status: ActivityStatus
  seckillPrice: number | null
  stock: number | null
  totalStock: number | null
  limitPerUser: number | null
}

/**
 * `POST /api/seckill/execute` 的业务结果枚举（已按真实返回核对）：
 * 成功入队是 **`QUEUED`**（不是 QUEUING），其余由控制器折叠为
 * `SOLD_OUT / DUPLICATED / RATE_LIMITED / REJECTED`。
 */
export type SeckillResult = 'QUEUED' | 'SOLD_OUT' | 'DUPLICATED' | 'RATE_LIMITED' | 'REJECTED'

/** `SeckillResponse`：`orderNo` 可用于立即轮询订单状态。 */
export interface SeckillExecuteResult {
  activityId: Id
  userId: Id
  result: SeckillResult
  orderNo: string | null
}

export interface RankEntry {
  rank: number
  userId: Id
  orderNo: string | null
}
