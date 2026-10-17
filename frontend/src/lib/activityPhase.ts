import type { ActivityItem, ActivityStatus } from '../types/activity'
import { parseServerTime } from './format'

/**
 * 售卖阶段（前端展示用状态机）。
 * 依据后端 `status` + 时间窗 + 库存/预热情况推导：
 * - 未预热：Redis 库存键不存在（详情 stock === null / 列表 preheated === false）
 * - 未开始 / 进行中 / 已售罄 / 已结束 / 已取消 / 未上架
 */
export type Phase = 'PREHEAT_MISSING' | 'NOT_STARTED' | 'IN_PROGRESS' | 'SOLD_OUT' | 'ENDED' | 'CANCELLED'

export interface PhaseInput {
  status: ActivityStatus | undefined
  startTime: string | undefined
  endTime: string | undefined
  stock: number | null | undefined
  /** 仅列表接口提供；详情用 `stock === null` 判定未预热。 */
  preheated?: boolean | null
  now?: number
}

export function resolvePhase(input: PhaseInput): Phase {
  const now = input.now ?? Date.now()
  const start = parseServerTime(input.startTime)
  const end = parseServerTime(input.endTime)

  if (input.status === 'CANCELLED') {
    return 'CANCELLED'
  }

  // 重要：后端的 activity.status 是**创建时写入的快照，不会随时间自动翻转**
  // （代码注释明确："DB 的 status 是创建时快照、不会自动翻转"）。
  // 因此时间窗优先于 status，否则会出现"已到开始时间但仍显示未开始、按钮被禁用"的错误。
  if (end && end.getTime() <= now) {
    return 'ENDED'
  }
  if (input.status === 'ENDED') {
    return 'ENDED'
  }
  if (start && start.getTime() > now) {
    return 'NOT_STARTED'
  }
  if (input.status === 'NOT_STARTED' && !start) {
    return 'NOT_STARTED'
  }

  // 未预热：列表有 preheated 标记；详情没有该字段，用 stock 为 null 判定
  const hasStockValue = input.stock !== null && input.stock !== undefined
  const preheated = input.preheated ?? hasStockValue
  if (!preheated) {
    return 'PREHEAT_MISSING'
  }

  // 后端已判定售罄（真实枚举 SOLD_OUT）或本地库存为 0
  if (input.status === 'SOLD_OUT') {
    return 'SOLD_OUT'
  }
  if (hasStockValue && (input.stock as number) <= 0) {
    return 'SOLD_OUT'
  }
  return 'IN_PROGRESS'
}

export const PHASE_TEXT: Record<Phase, string> = {
  PREHEAT_MISSING: '未预热',
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  SOLD_OUT: '已售罄',
  ENDED: '已结束',
  CANCELLED: '已取消',
}

export type PhaseTone = 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

export const PHASE_TONE: Record<Phase, PhaseTone> = {
  PREHEAT_MISSING: 'warning',
  NOT_STARTED: 'info',
  IN_PROGRESS: 'success',
  SOLD_OUT: 'neutral',
  ENDED: 'neutral',
  CANCELLED: 'neutral',
}

/** 不可抢购的原因（用于按钮禁用提示）。 */
export function phaseHint(phase: Phase): string | null {
  switch (phase) {
    case 'PREHEAT_MISSING':
      return '活动库存尚未预热，暂时无法抢购'
    case 'NOT_STARTED':
      return '活动还未开始，请等待倒计时结束'
    case 'SOLD_OUT':
      return '库存已抢光，下次早点来'
    case 'ENDED':
      return '活动已结束'
    case 'CANCELLED':
      return '活动已取消'
    default:
      return null
  }
}

/** 从列表项推导阶段（列表接口字段最全）。 */
export function phaseOfItem(item: ActivityItem, now?: number): Phase {
  return resolvePhase({
    status: item.status,
    startTime: item.startTime,
    endTime: item.endTime,
    stock: item.stock,
    preheated: item.preheated,
    now,
  })
}
