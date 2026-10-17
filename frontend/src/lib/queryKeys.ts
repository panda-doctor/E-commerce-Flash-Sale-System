import type { QueryClient } from '@tanstack/react-query'
import type { ProductQuery } from '../types/product'

/**
 * 查询键工厂：全工程唯一的键来源。
 *
 * 为什么集中管理：键既用于取数、也用于失效/清理。散落在页面与 Hook 里时，
 * 只要有一处写错（例如漏了用户标识），就会出现"换账号看到别人订单"这类问题。
 * 订单类键**必须带 userId**，未登录时用 `'anonymous'`，从键层面隔离用户数据。
 */
export const queryKeys = {
  products: (query: ProductQuery) => ['products', query] as const,
  product: (productId: string) => ['product', productId] as const,

  activities: () => ['activities'] as const,
  activity: (activityId: string) => ['activity', activityId] as const,
  rank: (activityId: string) => ['rank', activityId] as const,

  orders: (userId: string, activityId?: string) => ['orders', userId, activityId ?? 'all'] as const,
  order: (userId: string, orderNo: string) => ['order', userId, orderNo] as const,

  authMe: () => ['auth', 'me'] as const,

  /** 管理端指标：键里带令牌指纹，切换管理员令牌不会复用上一位的缓存。 */
  adminMetrics: (tokenFingerprint: string, activityId: string) =>
    ['admin', 'metrics', tokenFingerprint, activityId] as const,
}

/**
 * 令牌指纹：指标缓存按"管理员令牌"隔离，但**不能把令牌原文写进查询键**
 * （键会被 Devtools / 日志打印）。这里只取 djb2 哈希，不可逆且足够区分。
 */
export function tokenFingerprint(token: string | null | undefined): string {
  if (!token) {
    return 'none'
  }
  let hash = 5381
  for (let index = 0; index < token.length; index += 1) {
    hash = ((hash << 5) + hash + token.charCodeAt(index)) | 0
  }
  return (hash >>> 0).toString(36)
}

/** 用户域缓存前缀（登出 / 40100 / 换号时整体清除）。 */
export const USER_SCOPED_ROOTS = ['orders', 'order'] as const

export function clearUserScopedCache(queryClient: QueryClient): void {
  for (const root of USER_SCOPED_ROOTS) {
    queryClient.removeQueries({ queryKey: [root] })
  }
}
