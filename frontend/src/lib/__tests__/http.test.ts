import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, ErrorCode, http, onUnauthorized, request, setCsrfToken } from '../http'

/** `vi.fn` 的 calls 元素类型在严格模式下是空元组，统一用这个辅助函数取第 2 个参数。 */
const initOf = (mock: { mock: { calls: unknown[][] } }, index: number): RequestInit => {
  const call = mock.mock.calls[index] as unknown as [string, RequestInit]
  return call[1]
}

const jsonResponse = (body: string, status = 200) =>
  new Response(body, { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
  setCsrfToken(null)
  onUnauthorized(null)
})

describe('信封解析', () => {
  it('code=0 时返回 data', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse('{"code":0,"message":"ok","data":{"name":"耳机"}}'))))
    await expect(http.get<{ name: string }>('/api/products')).resolves.toEqual({ name: '耳机' })
  })

  it('code!==0 抛出 ApiError，保留后端 message 与 code', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(jsonResponse('{"code":40902,"message":"库存不足","data":null}'))),
    )
    await expect(http.get('/api/seckill/execute')).rejects.toMatchObject({
      name: 'ApiError',
      code: ErrorCode.SOLD_OUT,
      message: '库存不足',
    })
  })

  it('超大整数（Long）转成字符串，避免精度丢失', async () => {
    const body = '{"code":0,"message":"ok","data":{"activityId":1788829276582000000,"stock":5}}'
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse(body))))

    const data = await http.get<{ activityId: string; stock: number }>('/api/seckill/activities/1')
    expect(data.activityId).toBe('1788829276582000000')
    expect(typeof data.activityId).toBe('string')
    // 普通数值不应被误改
    expect(data.stock).toBe(5)
  })

  it('字符串内容里的长数字不会被加引号破坏', async () => {
    const body = '{"code":0,"message":"ok","data":{"name":"订单号 SK202610161302501540000","id":123}}'
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse(body))))

    const data = await http.get<{ name: string; id: number }>('/api/x')
    expect(data.name).toBe('订单号 SK202610161302501540000')
    expect(data.id).toBe(123)
  })
})

describe('CSRF 与登录态', () => {
  it('写操作自动带 X-CSRF-Token', async () => {
    setCsrfToken('csrf-abc')
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse('{"code":0,"message":"ok","data":null}')))
    vi.stubGlobal('fetch', fetchMock)

    await http.post('/api/seckill/execute', { activityId: '1' })

    const init = initOf(fetchMock, 0)
    expect((init.headers as Record<string, string>)['X-CSRF-Token']).toBe('csrf-abc')
    expect(init.credentials).toBe('include')
  })

  it('40301 → 取新 CSRF 后重放一次成功', async () => {
    setCsrfToken('stale')
    let call = 0
    const fetchMock = vi.fn((url: string) => {
      call += 1
      if (url.includes('/api/auth/csrf')) {
        return Promise.resolve(jsonResponse('{"code":0,"message":"ok","data":"fresh"}'))
      }
      if (call === 1) {
        return Promise.resolve(jsonResponse('{"code":40301,"message":"CSRF 校验失败","data":null}'))
      }
      return Promise.resolve(jsonResponse('{"code":0,"message":"ok","data":{"ok":true}}'))
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(http.post('/api/seckill/execute', {})).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    const retryInit = initOf(fetchMock, 2)
    expect((retryInit.headers as Record<string, string>)['X-CSRF-Token']).toBe('fresh')
  })

  it('40100 触发全局登出回调', async () => {
    const onExpired = vi.fn()
    onUnauthorized(onExpired)
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(jsonResponse('{"code":40100,"message":"请先登录","data":null}'))),
    )

    await expect(request('/api/auth/me')).rejects.toBeInstanceOf(ApiError)
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it('网络异常统一转成 ApiError，便于页面按文案展示', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('failed to fetch'))))

    await expect(http.get('/api/products')).rejects.toMatchObject({
      name: 'ApiError',
      code: ErrorCode.SYSTEM_ERROR,
    })
  })
})
