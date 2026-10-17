import { Suspense } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, useRoutes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { routes } from '../index'
import { AuthProvider } from '../../stores/auth'
import { ErrorBoundary } from '../../components/system/ErrorBoundary'

/**
 * 回归：管理端三个页面都在组件顶部调用 `useAdminToken()`。
 * 若 `AdminTokenProvider` 挂在页面内部的 `AdminShell` 里，首次渲染时 hook 在 Provider 之外，
 * 直接抛「useAdminToken 必须在 <AdminTokenProvider> 内使用」→ /admin 整页变"页面出现异常"。
 *
 * 这里用**真实路由表**渲染，确保 Provider 的位置被测试锁定，而不是只测某个孤立组件。
 */
const json = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

function RoutesProbe() {
  return useRoutes(routes)
}

function renderPath(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <ErrorBoundary>
            <Suspense fallback={<p>loading</p>}>
              <RoutesProbe />
            </Suspense>
          </ErrorBoundary>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('管理端路由：令牌状态必须在页面组件之上可用', () => {
  it.each(['/admin', '/admin/products', '/admin/activities'])(
    '%s 未载入令牌时展示令牌闸门，而不是渲染异常',
    async (path) => {
      vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL) => {
          const url = String(input)
          if (url.includes('/api/auth/me')) {
            return Promise.resolve(json({ code: 40100, message: '请先登录', data: null }))
          }
          if (url.includes('/api/seckill/activities')) {
            return Promise.resolve(json({ code: 0, message: 'ok', data: [] }))
          }
          return Promise.resolve(json({ code: 0, message: 'ok', data: null }))
        }),
      )

      renderPath(path)

      expect(await screen.findByText('需要管理令牌')).toBeInTheDocument()
      expect(screen.queryByText('页面出现异常')).toBeNull()
      expect(screen.queryByText(/useAdminToken 必须在/)).toBeNull()
    },
  )
})
