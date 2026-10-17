import { http } from '../lib/http'
import type { OrderDetail, OrderListItem } from '../types/order'

/** GET /api/seckill/users/me/orders?activityId= → 当前登录用户订单（activityId 可选）。 */
export function fetchMyOrders(activityId?: string) {
  const query = activityId ? `?activityId=${encodeURIComponent(activityId)}` : ''
  return http.get<OrderListItem[]>(`/api/seckill/users/me/orders${query}`)
}

/** GET /api/seckill/orders/{orderNo} → 订单详情（后端校验归属）。 */
export function fetchOrder(orderNo: string) {
  return http.get<OrderDetail>(`/api/seckill/orders/${encodeURIComponent(orderNo)}`)
}
