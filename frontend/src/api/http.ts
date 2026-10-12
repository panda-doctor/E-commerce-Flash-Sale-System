import { ApiError } from '../types/api'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
let csrfToken: string | null = null

export function setCsrfToken(token: string | null): void {
  csrfToken = token
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isForm = typeof FormData !== 'undefined' && init.body instanceof FormData
  const headers = new Headers(init.headers)
  const adminToken = import.meta.env.VITE_ADMIN_TOKEN
  const method = (init.method ?? 'GET').toUpperCase()

  if (!isForm && init.body != null) headers.set('Content-Type', 'application/json')
  if (adminToken) headers.set('X-Admin-Token', adminToken)
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)
    && (path.startsWith('/api/seckill/') || path === '/api/auth/logout') && csrfToken) {
    headers.set('X-CSRF-Token', csrfToken)
  }

  let response: Response
  try {
    response = await fetch(`${baseUrl}${path}`, { ...init, headers, credentials: 'include' })
  } catch (error) {
    throw new ApiError(-1, '无法连接后端服务，请检查服务状态和代理配置。', error)
  }

  let payload: unknown
  try {
    const raw = await response.text()
    // Preserve Java Long identifiers as strings before JSON.parse can round them past 2^53.
    const losslessIds = raw.replace(/("(?:activityId|productId|userId|orderId|id)"\s*:\s*)(-?\d+)(?=\s*[,}])/g, '$1"$2"')
    payload = JSON.parse(losslessIds) as unknown
  } catch (error) {
    throw new ApiError(response.status, `后端响应无法解析（HTTP ${response.status}）。`, error)
  }

  if (typeof payload !== 'object' || payload === null || !('code' in payload)) {
    throw new ApiError(response.status, `后端返回了无法识别的响应（HTTP ${response.status}）。`, payload)
  }

  const envelope = payload as { code: number; message?: string; data?: T }
  if (envelope.code !== 0) {
    throw new ApiError(envelope.code, envelope.message || '请求失败。', envelope.data)
  }
  return envelope.data as T
}

function queryString(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  const value = query.toString()
  return value ? `?${value}` : ''
}

export const http = {
  get: <T>(path: string, params: Record<string, string | number | undefined> = {}) =>
    request<T>(`${path}${queryString(params)}`),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  postForm: <T>(path: string, body: FormData) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
