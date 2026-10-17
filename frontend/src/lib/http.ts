import type { Result } from '../types/api'

/** 后端错误码（真实值，见 ResultCode）。 */
export const ErrorCode = {
  SUCCESS: 0,
  UNAUTHORIZED: 40100,
  BAD_CREDENTIALS: 40101,
  CSRF_INVALID: 40301,
  BAD_REQUEST: 40001,
  DUPLICATED: 40901,
  SOLD_OUT: 40902,
  RATE_LIMITED: 42900,
  SYSTEM_ERROR: 50000,
} as const

export class ApiError extends Error {
  readonly code: number
  readonly httpStatus: number
  readonly data: unknown

  constructor(message: string, code: number, httpStatus: number, data?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.data = data
  }
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

/** CSRF token 只存内存（刷新后由 /api/auth/me + /api/auth/csrf 重新获取）。 */
let csrfToken: string | null = null

export function setCsrfToken(token: string | null) {
  csrfToken = token
}

export function getCsrfToken() {
  return csrfToken
}

type UnauthorizedHandler = () => void
let unauthorizedHandler: UnauthorizedHandler | null = null

/** 由 AuthProvider 注册：任意请求返回 40100 时统一登出并跳登录。 */
export function onUnauthorized(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler
}

/**
 * 大整数保护：后端 Long 以 number 返回，超过 2^53-1 会丢精度。
 * 仅当长数字出现在「结构位置」（紧跟 : [ , 且后接 , } ]）时才加引号，
 * 避免误伤字符串内容。
 */
const BIG_INT_LITERAL = /(?<=[:[,]\s*)(-?\d{16,})(?=\s*[,}\]])/g

function parseEnvelope<T>(text: string): Result<T> | null {
  if (!text) {
    return null
  }
  const guarded = text.replace(BIG_INT_LITERAL, '"$1"')
  return JSON.parse(guarded) as Result<T>
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /** 管理端令牌（仅 /api/admin/** 使用）。 */
  adminToken?: string | null
  /** 管理端上传用：直接传 FormData，不设置 Content-Type。 */
  formData?: FormData
  signal?: AbortSignal
  timeoutMs?: number
}

function buildHeaders(options: RequestOptions, withCsrf: boolean): HeadersInit {
  const headers: Record<string, string> = { Accept: 'application/json' }

  if (!options.formData && options.body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8'
  }
  if (withCsrf && csrfToken) {
    headers['X-CSRF-Token'] = csrfToken
  }
  if (options.adminToken) {
    headers['X-Admin-Token'] = options.adminToken
  }
  return headers
}

/**
 * 取新的 CSRF token 并**写回内存**。
 * 注意：只取不存会让 40301 重试继续用旧 token（本函数曾漏掉写回，已由单测锁死）。
 */
async function refreshCsrf(): Promise<void> {
  const token = await request<string>('/api/auth/csrf', { method: 'GET' })
  if (typeof token === 'string' && token.length > 0) {
    setCsrfToken(token)
  }
}

function toApiError(error: unknown, httpStatus: number): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  const message = error instanceof Error ? error.message : '网络异常，请稍后重试'
  return new ApiError(message, ErrorCode.SYSTEM_ERROR, httpStatus)
}

/**
 * 统一请求：解析信封 → code!==0 抛 ApiError → 40301 自动取新 CSRF 后重放一次
 * → 40100 触发全局登出。
 *
 * 实现上使用 Promise 链而不是 try/catch（项目规范），错误统一转为 ApiError。
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const isWrite = method !== 'GET'
  const url = `${API_BASE}${path}`
  const timeoutMs = options.timeoutMs ?? 15_000

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeoutMs)
  if (options.signal) {
    options.signal.addEventListener('abort', () => controller.abort(), { once: true })
  }

  const execute = (withCsrf: boolean) =>
    fetch(url, {
      method,
      credentials: 'include',
      headers: buildHeaders(options, withCsrf),
      body: options.formData ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
      signal: controller.signal,
    })
      .then(async (response) => {
        const text = await response.text()
        const envelope = parseEnvelope<T>(text)
        if (!envelope) {
          throw new ApiError('服务端返回为空或格式异常', ErrorCode.SYSTEM_ERROR, response.status)
        }
        if (envelope.code !== ErrorCode.SUCCESS) {
          throw new ApiError(envelope.message || '请求失败', envelope.code, response.status, envelope.data)
        }
        return envelope.data
      })
      .catch((error: unknown) => {
        throw toApiError(error, 0)
      })

  return execute(isWrite)
    .catch(async (error: unknown) => {
      const apiError = error instanceof ApiError ? error : toApiError(error, 0)

      if (apiError.code === ErrorCode.CSRF_INVALID && isWrite) {
        // CSRF 可能因会话续期而轮换：取新值后重放一次，避免用户无感失败
        await refreshCsrf().catch(() => undefined)
        return execute(true)
      }
      if (apiError.code === ErrorCode.UNAUTHORIZED) {
        unauthorizedHandler?.()
      }
      throw apiError
    })
    .finally(() => window.clearTimeout(timer))
}

export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  upload: <T>(path: string, formData: FormData, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', formData }),
}
