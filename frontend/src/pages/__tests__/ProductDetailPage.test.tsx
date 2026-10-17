import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProductDetailPage from '../ProductDetailPage'

/**
 * 回归：路由参数 `productId` 是字符串，后端返回的 `productId` 曾是数字，
 * `item.productId === productId` 恒为 false ⇒ 商品详情永远显示"没有秒杀活动"。
 * 现在 API 层统一归一化，这里用**数字 ID 的响应**锁定该行为。
 */
const json = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

function renderDetail(productId: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/products/${productId}`]}>
        <Routes>
          <Route path="/products/:productId" element={<ProductDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('商品详情：关联活动匹配', () => {
  it('后端返回数字 ID 时，仍能匹配到关联活动并给出秒杀入口', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('/api/seckill/activities')) {
          return Promise.resolve(
            json({
              code: 0,
              message: 'ok',
              data: [
                {
                  activityId: 90295171892224,
                  productId: 90173199893504,
                  activityName: '回归-限时秒杀',
                  productName: 'P4D4-ProdA',
                  productImage: null,
                  startTime: '2026-10-17 20:00:00',
                  endTime: '2026-10-17 23:00:00',
                  seckillPrice: 1999,
                  originalPrice: 39900,
                  status: 'RUNNING',
                  stock: 2,
                  preheated: true,
                  totalStock: 3,
                  limitPerUser: 1,
                },
              ],
            }),
          )
        }
        return Promise.resolve(
          json({
            code: 0,
            message: 'ok',
            data: {
              productId: 90173199893504,
              name: 'P4D4-ProdA',
              description: '联调商品',
              imageUrl: null,
              originalPrice: 39900,
              totalStock: 20,
              availableStock: 20,
              status: 'ON_SHELF',
            },
          }),
        )
      }),
    )

    renderDetail('90173199893504')

    expect(await screen.findByText('P4D4-ProdA')).toBeInTheDocument()
    expect(screen.getByText('有秒杀活动')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '去秒杀' })).toHaveAttribute(
      'href',
      '/activities/90295171892224',
    )
    expect(screen.queryByText(/没有进行中的秒杀活动/)).toBeNull()
  })
})
