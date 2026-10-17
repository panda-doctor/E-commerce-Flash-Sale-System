import { http } from '../lib/http'
import { toId, toOptionalId } from '../lib/id'
import type { OrderDetail, OrderListItem } from '../types/order'

/** 后端 13~15 位主键仍是 JSON number，入口处统一转字符串（见 lib/id.ts）。 */
function normalizeItem(order: OrderListItem): OrderListItem {
  return {
    ...order,
    activityId: toId(order.activityId),
    productId: toId(order.productId),
  }
}

function normalizeDetail(order: OrderDetail): OrderDetail {
  return {
    ...order,
    activityId: toOptionalId(order.activityId),
    productId: toOptionalId(order.productId),
    userId: toOptionalId(order.userId),
  }
}

/** GET /api/seckill/users/me/orders?activityId= → 当前登录用户订单（activityId 可选）。 */
export function fetchMyOrders(activityId?: string) {
  const query = activityId ? `?activityId=${encodeURIComponent(activityId)}` : ''
  return http
    .get<OrderListItem[]>(`/api/seckill/users/me/orders${query}`)
    .then((list) => list.map(normalizeItem))
}

/** GET /api/seckill/orders/{orderNo} → 订单详情（后端校验归属）。 */
export function fetchOrder(orderNo: string) {
  return http
    .get<OrderDetail>(`/api/seckill/orders/${encodeURIComponent(orderNo)}`)
    .then(normalizeDetail)
}
