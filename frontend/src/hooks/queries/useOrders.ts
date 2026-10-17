import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchMyOrders, fetchOrder } from '../../api/order'
import { useDocumentVisible } from '../useDocumentVisible'
import { useAuth } from '../../stores/auth'
import { queryKeys } from '../../lib/queryKeys'
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

export interface PollingOptions {
  /** 两次查询之间的间隔，默认 1.5s。 */
  intervalMs?: number
  /** 累计轮询时长上限，默认 90s；到达后停止并提示可手动刷新。 */
  maxDurationMs?: number
  /** 连续失败次数上限，默认 5 次；到达后停止，避免网络异常时无限重试。 */
  maxFailures?: number
}

const POLLING_DEFAULTS = {
  intervalMs: 1500,
  maxDurationMs: 90_000,
  maxFailures: 5,
} as const

/**
 * 轮询决策（纯函数，便于单测）：
 * 页面隐藏 / 已终态 / 连续失败超限 / 超过时长上限 → 返回 false 停止调度；
 * 否则返回下一次的间隔。`elapsedMs` 为 0 表示"还没开始计时"（首次请求前）。
 */
export function nextPollDelay(input: {
  visible: boolean
  status: OrderStatus | undefined
  failureCount: number
  elapsedMs: number
  intervalMs: number
  maxDurationMs: number
  maxFailures: number
}): number | false {
  if (!input.visible) {
    return false
  }
  if (isTerminalStatus(input.status)) {
    return false
  }
  if (input.failureCount >= input.maxFailures) {
    return false
  }
  if (input.elapsedMs >= input.maxDurationMs) {
    return false
  }
  return input.intervalMs
}

/** 轮询预算：解决"后端一直排队 / 接口一直报错"时的无限轮询。 */
function usePollingBudget(options: PollingOptions, resetKey: string | undefined) {
  const { intervalMs, maxDurationMs, maxFailures } = { ...POLLING_DEFAULTS, ...options }
  const startedAtRef = useRef<number | null>(null)

  // 换了订单/活动就重新计时，避免上一单的预算"继承"到下一单
  useEffect(() => {
    startedAtRef.current = null
  }, [resetKey])

  return { intervalMs, maxDurationMs, maxFailures, startedAtRef }
}

function elapsedSince(startedAt: number | null): number {
  return startedAt === null ? 0 : Date.now() - startedAt
}

/**
 * 我的订单：仅登录后启用。
 * `pollWhileQueuing` 时，列表里还有 QUEUING 就每 2s 刷新；全部终态/页面隐藏/超时即停。
 */
export function useMyOrders(enabled: boolean, options: { activityId?: string; pollWhileQueuing?: boolean } = {}) {
  const visible = useDocumentVisible()
  const { user } = useAuth()
  const { activityId, pollWhileQueuing = false } = options
  const userId = user?.id ?? 'anonymous'
  const budget = usePollingBudget({ intervalMs: 2000, maxDurationMs: 90_000 }, `${userId}:${activityId ?? 'all'}`)

  const query = useQuery({
    queryKey: queryKeys.orders(userId, activityId),
    queryFn: () => fetchMyOrders(activityId),
    enabled,
    staleTime: 0,
    refetchInterval: (current) => {
      if (!pollWhileQueuing) {
        return false
      }
      const hasQueuing = hasQueuingOrder(current.state.data)
      const delay = nextPollDelay({
        visible,
        // 列表接口：有排队中订单就等价于"非终态"，没有则视为终态
        status: hasQueuing ? 'QUEUING' : 'CREATED',
        failureCount: current.state.fetchFailureCount,
        elapsedMs: elapsedSince(budget.startedAtRef.current),
        intervalMs: budget.intervalMs,
        maxDurationMs: budget.maxDurationMs,
        maxFailures: budget.maxFailures,
      })
      if (delay && budget.startedAtRef.current === null) {
        budget.startedAtRef.current = Date.now()
      }
      return delay
    },
  })

  const pollingStopped =
    pollWhileQueuing &&
    enabled &&
    hasQueuingOrder(query.data) &&
    (query.failureCount >= budget.maxFailures ||
      elapsedSince(budget.startedAtRef.current) >= budget.maxDurationMs)

  return { ...query, pollingStopped }
}

/**
 * 订单详情轮询：QUEUING 时每 1.5s 拉一次；进入终态 / 页面隐藏 / 组件卸载 / 超时 / 连续失败即停。
 * 用 React Query 的 refetchInterval 回调实现，不叠加自定义 setInterval（同一订单天然只有一条链）。
 */
export function useOrderPolling(orderNo: string | undefined, enabled = true, options: PollingOptions = {}) {
  const visible = useDocumentVisible()
  const { user } = useAuth()
  const userId = user?.id ?? 'anonymous'
  const budget = usePollingBudget(options, orderNo)

  const query = useQuery({
    queryKey: queryKeys.order(userId, orderNo ?? 'none'),
    queryFn: () => fetchOrder(orderNo as string),
    enabled: Boolean(orderNo) && enabled,
    staleTime: 0,
    retry: 1,
    refetchInterval: (current) => {
      const delay = nextPollDelay({
        visible,
        status: current.state.data?.status,
        failureCount: current.state.fetchFailureCount,
        elapsedMs: elapsedSince(budget.startedAtRef.current),
        intervalMs: budget.intervalMs,
        maxDurationMs: budget.maxDurationMs,
        maxFailures: budget.maxFailures,
      })
      if (delay && budget.startedAtRef.current === null) {
        budget.startedAtRef.current = Date.now()
      }
      return delay
    },
  })

  const status = query.data?.status
  const pollingStopped =
    Boolean(orderNo) &&
    enabled &&
    !isTerminalStatus(status) &&
    (query.failureCount >= budget.maxFailures ||
      elapsedSince(budget.startedAtRef.current) >= budget.maxDurationMs)

  return { ...query, pollingStopped }
}
