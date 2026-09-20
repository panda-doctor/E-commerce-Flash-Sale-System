// 统一请求封装：基于 fetch + Vite dev proxy（/api -> http://localhost:8081）
// 后端统一返回 Result{ code, message, data, requestId, timestamp }：
//   code === 0 成功；业务异常（如 40901 重复秒杀 / 40902 库存不足 / 42900 限流）也走 HTTP 200，
//   通过 body.code 区分。故所有非 0 的 code 统一以 ApiError 抛出，由页面按 code 分支处理。

import { userStore, tokenOf } from '../utils/store'

export class ApiError extends Error {
  constructor(code, message, data = null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.data = data
  }
}

// 用户访问令牌按当前演示身份携带（tokenOf 为两级查找：静态演示账号 → 本地动态令牌缓存）：
//   静态账号匹配 → 用该账号令牌（后端 USER_TOKENS 静态白名单）；
//   自定义 userId → 先查本地「已领取」的动态令牌缓存（顶栏点「领取令牌」注册后写入）；
//   均未命中 → 回落 VITE_USER_TOKEN 兜底（绑 1001，绑定不一致时后端会拒）；
//   全部为空 → 直接不带，后端返回"用户访问令牌无效"。
function currentUserToken() {
  const bound = tokenOf(userStore.userId)
  return bound || import.meta.env.VITE_USER_TOKEN || ''
}

async function request(url, options = {}) {
  const method = options.method ?? 'GET'
  // multipart/FormData 由浏览器自动生成 Content-Type（含 boundary），不能手动指定
  const isForm = typeof FormData !== 'undefined' && options.body instanceof FormData
  let response
  try {
    const userToken = currentUserToken()
    const accessHeaders = {
      ...(import.meta.env.VITE_ADMIN_TOKEN ? { 'X-Admin-Token': import.meta.env.VITE_ADMIN_TOKEN } : {}),
      ...(userToken ? { 'X-User-Token': userToken } : {}),
    }
    response = await fetch(url, {
      method,
      headers: isForm
        ? { ...accessHeaders, ...(options.headers ?? {}) }
        : { 'Content-Type': 'application/json', ...accessHeaders, ...(options.headers ?? {}) },
      body:
        options.body == null
          ? undefined
          : isForm
            ? options.body
            : JSON.stringify(options.body),
    })
  } catch (e) {
    // 网络层失败（后端未启动 / 代理未通）
    throw new ApiError(-1, '无法连接后端服务，请确认后端已启动（:8081）', e)
  }

  let payload
  try {
    payload = await response.json()
  } catch (e) {
    throw new ApiError(response.status, `响应解析失败（HTTP ${response.status}）`, e)
  }

  if (payload && payload.code === 0) return payload.data
  if (payload && typeof payload.code === 'number') {
    throw new ApiError(payload.code, payload.message || '请求失败', payload.data)
  }
  // 非 Result 结构（网关/代理错误页等）
  throw new ApiError(response.status, `未知响应（HTTP ${response.status}）`, payload)
}

export const http = {
  get: (url, params) => {
    const qs = params
      ? '?' + Object.entries(params)
          .filter(([, v]) => v !== undefined && v !== null && v !== '')
          .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
          .join('&')
      : ''
    return request(url + qs)
  },
  post: (url, body) => request(url, { method: 'POST', body }),
  postForm: (url, formData) => request(url, { method: 'POST', body: formData }),
}
