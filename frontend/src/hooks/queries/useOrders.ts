import { useQuery } from '@tanstack/react-query'
import { fetchMyOrders, fetchOrder } from '../../api/order'
import { useDocumentVisible } from '../useDocumentVisible'
import type { OrderStatus } from '../../types/order'

/**
 * 终态：`CREATED`（本项目无支付环节，创建成功即终态）、`CREATE_FAILED`、`CANCELLED`。
 * 只有 `QUEUING` 需要继续轮询 —— 判错会导致"永远轮询"或"过早停止"。
 */
const TERMINAL: OrderStatus[] = ['CREATED', 'CREATE_FAILED', 'CANCELLED']

export function isTerminalStatus(status: OrderStatus | undefined): boolean {
  return status ? TERMINAL.includes(status) : false
}

export function hasQueuingOrder(orders: { status: OrderStatus }[] | undefined): boolean {
  return (orders ?? []).some((order) => order.status === 'QUEUING')
}

/**
 * 我的订单：仅登录后启用。
 * `pollWhileQueuing` 时，只要列表里还有 QUEUING 就每 2s 刷新；全部进入终态即自动停止。
 */
export function useMyOrders(enabled: boolean, options: { activityId?: string; pollWhileQueuing?: boolean } = {}) {
  const visible = useDocumentVisible()
  const { activityId, pollWhileQueuing = false } = options

  return useQuery({
    queryKey: ['orders', 'me', activityId ?? 'all'],
    queryFn: () => fetchMyOrders(activityId),
    enabled,
    staleTime: 0,
    refetchInterval: (query) => {
      if (!pollWhileQueuing || !visible) {
        return false
      }
      return hasQueuingOrder(query.state.data) ? 2000 : false
    },
  })
}

/**
 * 订单详情轮询：QUEUING 时每 1.5s 拉一次，进入终态/页面隐藏/组件卸载即停。
 * 用 React Query 的 refetchInterval 回调实现，不叠加自定义 setInterval。
 */
export function useOrderPolling(orderNo: string | undefined, enabled = true) {
  const visible = useDocumentVisible()

  return useQuery({
    queryKey: ['order', orderNo],
    queryFn: () => fetchOrder(orderNo as string),
    enabled: Boolean(orderNo) && enabled,
    staleTime: 0,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (!visible || isTerminalStatus(status)) {
        return false
      }
      return 1500
    },
  })
}
