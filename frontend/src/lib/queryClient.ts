import { QueryClient } from '@tanstack/react-query'

/**
 * 全局查询客户端。
 * - 不重试 4xx 业务错误（重试也不会成功，只会让用户多等）
 * - 窗口重新聚焦时刷新，保证秒杀库存/订单状态不至于长时间陈旧
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      retry: (failureCount, error) => {
        const code = (error as { code?: number } | null)?.code
        if (typeof code === 'number' && code >= 40000 && code < 50000) {
          return false
        }
        return failureCount < 1
      },
    },
    mutations: { retry: 0 },
  },
})
