import type { ActivityStatus, PreheatStatus, ProductStatus } from '../types/api'

export function moneyFromCents(cents: number | null | undefined): string {
  if (cents == null || !Number.isFinite(cents)) return '—'
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(cents / 100)
}

export function centsFromYuan(yuan: string): number {
  const value = yuan.trim()
  if (!/^\d+(\.\d{1,2})?$/.test(value)) throw new Error('金额最多保留两位小数。')
  const [whole, fraction = ''] = value.split('.')
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
  if (cents > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('金额超出当前接口可安全处理的范围。')
  return Number(cents)
}

export function moneyToYuan(cents: number | null | undefined): string {
  if (cents == null || !Number.isFinite(cents)) return ''
  return (cents / 100).toFixed(2)
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ')
}

export const activityStatusLabel: Record<ActivityStatus, string> = {
  NOT_STARTED: '未开始', RUNNING: '进行中', ENDED: '已结束', SOLD_OUT: '已售罄', CANCELLED: '已取消',
}

export const preheatStatusLabel: Record<PreheatStatus, string> = {
  NOT_PREHEATED: '未预热', PREHEATED: '已预热', PREHEAT_FAILED: '预热失败',
}

export const productStatusLabel: Record<ProductStatus, string> = {
  ON_SHELF: '已上架', OFF_SHELF: '已下架',
}

export function readableError(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试。'
}

export function activityPreheatLabel(preheated?: boolean | null): string {
  return preheated === false ? preheatStatusLabel.NOT_PREHEATED : preheatStatusLabel.PREHEATED
}
