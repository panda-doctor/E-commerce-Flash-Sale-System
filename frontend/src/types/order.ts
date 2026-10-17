import type { Id } from './api'

/**
 * 订单状态枚举（`OrderStatusEnum`，已按真实代码核对）：
 * `QUEUING(0,"排队中") / CREATED(1,"已创建") / CREATE_FAILED(2,"创建失败") / CANCELLED(3,"已取消")`。
 * 本项目无支付环节，**`CREATED` 即最终成功态**；`QUEUING` 才是非终态。
 */
export type OrderStatus = 'QUEUING' | 'CREATED' | 'CREATE_FAILED' | 'CANCELLED' | string

export interface OrderListItem {
  orderNo: string
  activityId: Id
  productId: Id
  status: OrderStatus
  createdAt: string
}

/**
 * 订单详情：`OrderController.getOrderStatus` 返回 Map，
 * 真实字段为 orderNo/activityId/productId/userId/status/seckillPrice/createdAt/updatedAt，
 * 因此这里全部可选，页面按存在性渲染（不臆造字段）。
 */
export interface OrderDetail {
  orderNo: string
  activityId?: Id
  productId?: Id
  userId?: Id
  status?: OrderStatus
  seckillPrice?: number | null
  createdAt?: string
  updatedAt?: string
  [key: string]: unknown
}
