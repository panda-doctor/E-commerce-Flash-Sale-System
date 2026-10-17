import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProductListPage from '../ProductListPage'

/** 组件级冒烟：真实渲染列表页，用信封格式的假响应驱动，验证"数据 → 界面"这一段。 */
function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductListPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const json = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

describe('ProductListPage 冒烟', () => {
  it('渲染真实信封数据：商品名、价格、总数与分页', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('/api/products')) {
          return Promise.resolve(
            json({
              code: 0,
              message: 'ok',
              data: {
                records: [
                  {
                    productId: '90173199893504',
                    name: '静音机械键盘 K8',
                    description: '热插拔轴体',
                    imageUrl: null,
                    originalPrice: 39900,
                    totalStock: 20,
                    availableStock: 12,
                    status: 'ON_SHELF',
                  },
                ],
                page: 1,
                pageSize: 12,
                total: 1,
                pages: 1,
              },
            }),
          )
        }
        // 活动列表：返回空数组（首页不渲染促销条也不应报错）
        return Promise.resolve(json({ code: 0, message: 'ok', data: [] }))
      }),
    )

    renderPage()

    expect(await screen.findByText('静音机械键盘 K8')).toBeInTheDocument()
    expect(screen.getByText('¥399.00')).toBeInTheDocument()
    expect(screen.getByText('共 1 件商品')).toBeInTheDocument()
    expect(screen.getByText(/剩余 12/)).toBeInTheDocument()
    expect(screen.getByText('当前没有进行中的活动，先看看在售好物。')).toBeInTheDocument()
  })

  it('空数据显示空态而不是报错', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        // 活动列表必须返回数组：两条接口共用同一个 mock 时要按 URL 分流
        if (url.includes('/api/seckill/activities')) {
          return Promise.resolve(json({ code: 0, message: 'ok', data: [] }))
        }
        return Promise.resolve(
          json({
            code: 0,
            message: 'ok',
            data: { records: [], page: 1, pageSize: 12, total: 0, pages: 0 },
          }),
        )
      }),
    )

    renderPage()

    expect(await screen.findByText('暂时没有在售商品')).toBeInTheDocument()
  })

  it('业务错误码（非 0）渲染错误态与重试按钮', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          json({ code: 50000, message: '依赖不可用', data: null }),
        ),
      ),
    )

    renderPage()

    expect(await screen.findByText('加载失败')).toBeInTheDocument()
    expect(screen.getByText('依赖不可用')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /重新加载/ })).toBeInTheDocument()
  })
})
