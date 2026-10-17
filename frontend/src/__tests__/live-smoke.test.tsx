import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { request as httpRequest } from 'node:http'
import ProductListPage from '../pages/ProductListPage'
import ProductDetailPage from '../pages/ProductDetailPage'
import ActivityListPage from '../pages/ActivityListPage'
import ActivityDetailPage from '../pages/ActivityDetailPage'
import OrdersPage from '../pages/OrdersPage'
import { AuthProvider } from '../stores/auth'

/**
 * 真后端渲染冒烟：只有显式提供 `VITE_API_BASE_URL` 时才运行，避免日常 `npm test` 依赖后端。
 *
 * 为什么要自带 fetch：jsdom 的 `AbortController` 不是 node fetch 认识的实例，
 * 直接调用会抛 `RequestInit: Expected signal to be an instance of AbortSignal`（测试环境假象）。
 * 这里用 `node:http` 实现最小 fetch（忽略 signal），让**真实响应体**进入 React，
 * 从而捕获"编译能过、真数据一来就崩"的运行期错误。
 */
const LIVE = Boolean(import.meta.env.VITE_API_BASE_URL)
const API = import.meta.env.VITE_API_BASE_URL as string

function nodeFetch(url: string, init?: RequestInit): Promise<Response> {
  const target = new URL(url, 'http://127.0.0.1')
  const body = typeof init?.body === 'string' ? init.body : undefined
  const headers = (init?.headers ?? {}) as Record<string, string>

  return new Promise((resolve, reject) => {
    const req = httpRequest(
      {
        hostname: target.hostname,
        port: target.port || 80,
        path: `${target.pathname}${target.search}`,
        method: init?.method ?? 'GET',
        headers,
      },
      (res) => {
        const chunks: Buffer[] = []
        res.on('data', (chunk: Buffer) => chunks.push(chunk))
        res.on('end', () => {
          resolve(
            new Response(Buffer.concat(chunks).toString('utf8'), {
              status: res.statusCode ?? 200,
              headers: { 'Content-Type': res.headers['content-type'] ?? 'application/json' },
            }),
          )
        })
      },
    )
    req.on('error', reject)
    if (body) {
      req.write(body)
    }
    req.end()
  })
}

function renderAt(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/" element={<ProductListPage />} />
            <Route path="/products" element={<ProductListPage />} />
            <Route path="/products/:productId" element={<ProductDetailPage />} />
            <Route path="/activities" element={<ActivityListPage />} />
            <Route path="/activities/:activityId" element={<ActivityDetailPage />} />
            <Route path="/orders" element={<OrdersPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

async function readJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API}${path}`)
  return (await response.json()) as T
}

describe.runIf(LIVE)('真后端渲染冒烟', () => {
  // 必须每个用例都重新 stub：测试隔离会在用例间还原全局对象，
  // 否则从第 2 个用例起会落回 jsdom 原生 fetch，抛 AbortSignal 类型不匹配的假错。
  beforeEach(() => {
    vi.stubGlobal('fetch', nodeFetch)
  })

  it('商品列表渲染真实数据（含价格与详情链接）', async () => {
    renderAt('/products')
    expect(await screen.findByText(/共 \d+ 件商品/, {}, { timeout: 20_000 })).toBeInTheDocument()
    await waitFor(
      () => {
        expect(document.querySelectorAll('a[href^="/products/"]').length).toBeGreaterThan(0)
      },
      { timeout: 20_000 },
    )
    expect(document.body.textContent).toMatch(/¥\d/)
  }, 40_000)

  it('商品详情按真实 productId 渲染（价格/库存/秒杀入口）', async () => {
    const list = await readJson<{ data: { records: Array<{ productId: string }> } }>(
      '/api/products?page=1&pageSize=1',
    )
    const productId = list.data.records[0]?.productId
    expect(productId).toBeTruthy()

    renderAt(`/products/${productId}`)
    expect(await screen.findByRole('heading', { level: 1 }, { timeout: 20_000 })).toBeInTheDocument()
    expect(document.body.textContent).toMatch(/¥\d/)
    // 不能落到错误态
    expect(screen.queryByText('加载失败')).toBeNull()
  }, 40_000)

  it('活动详情按真实 activityId 渲染交易面板与榜单', async () => {
    const list = await readJson<{ data: Array<{ activityId: string }> }>('/api/seckill/activities')
    const activityId = list.data[0]?.activityId
    expect(activityId).toBeTruthy()

    renderAt(`/activities/${activityId}`)
    expect(await screen.findByText(/抢购榜 TOP 10/, {}, { timeout: 20_000 })).toBeInTheDocument()
    expect(document.body.textContent).toMatch(/¥\d/)
    expect(screen.getByRole('button', { name: /立即秒杀|未开始|已结束|已售罄|暂不可/ })).toBeInTheDocument()
  }, 40_000)

  it('未登录访问我的订单：可读提示，不崩溃', async () => {
    renderAt('/orders')
    await waitFor(
      () => {
        expect(document.body.textContent ?? '').toMatch(/登录|加载失败|订单/)
      },
      { timeout: 20_000 },
    )
  }, 40_000)

  it('活动列表不崩且能渲染标题', async () => {
    renderAt('/activities')
    expect(await screen.findByText('秒杀活动', {}, { timeout: 20_000 })).toBeInTheDocument()
  }, 40_000)
})
