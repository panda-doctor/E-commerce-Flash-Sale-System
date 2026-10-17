import { ApiError, ErrorCode } from './http'
import type { SeckillResult } from '../types/activity'

/** 错误码 → 面向用户的中文文案（后端 message 优先，这里只做兜底与补充）。 */
const CODE_TEXT: Record<number, string> = {
  [ErrorCode.UNAUTHORIZED]: '请先登录后再操作',
  [ErrorCode.BAD_CREDENTIALS]: '账号或密码不正确',
  [ErrorCode.CSRF_INVALID]: '页面安全校验已过期，请重试',
  [ErrorCode.BAD_REQUEST]: '请求参数有误，请检查后重试',
  [ErrorCode.DUPLICATED]: '你已经抢到过这件商品了',
  [ErrorCode.SOLD_OUT]: '手慢了，库存已被抢光',
  [ErrorCode.RATE_LIMITED]: '请求过于频繁，请稍后再试',
  [ErrorCode.SYSTEM_ERROR]: '服务暂时不可用，请稍后重试',
}

export function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.message) {
      return error.message
    }
    return CODE_TEXT[error.code] ?? '操作失败，请稍后重试'
  }
  if (error instanceof Error && error.name === 'AbortError') {
    return '请求超时，请重试'
  }
  return '网络异常，请检查网络后重试'
}

/** 秒杀业务结果 → 展示文案（键为后端真实返回值，入队是 `QUEUED`）。 */
export const SECKILL_RESULT_TEXT: Record<SeckillResult, string> = {
  QUEUED: '已进入抢购队列，正在为你排队生成订单',
  SOLD_OUT: '手慢了，库存已被抢光',
  DUPLICATED: '你已经抢到过这件商品了',
  RATE_LIMITED: '操作过于频繁，请稍后重试',
  REJECTED: '活动暂不可参与，请确认活动状态',
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.code === ErrorCode.UNAUTHORIZED
}
