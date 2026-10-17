import { describe, expect, it } from 'vitest'
import {
  formatDateTime,
  formatPrice,
  formatRemaining,
  parseServerTime,
  stockPercent,
} from '../format'

describe('金额格式化（后端单位为分）', () => {
  it('分 → 元并保留两位小数', () => {
    expect(formatPrice(19900)).toBe('¥199.00')
    expect(formatPrice(1)).toBe('¥0.01')
    expect(formatPrice(0)).toBe('¥0.00')
  })

  it('空值显示占位符而不是 NaN', () => {
    expect(formatPrice(null)).toBe('—')
    expect(formatPrice(undefined)).toBe('—')
  })
})

describe('服务端时间解析', () => {
  it('兼容 LocalDateTime 的 T 分隔与空格分隔', () => {
    const withT = parseServerTime('2026-10-17T13:25:00')
    const withSpace = parseServerTime('2026-10-17 13:25:00')
    expect(withT?.getTime()).toBe(withSpace?.getTime())
    expect(withT?.getFullYear()).toBe(2026)
  })

  it('非法时间返回 null，展示为占位符', () => {
    expect(parseServerTime('not-a-date')).toBeNull()
    expect(formatDateTime('not-a-date')).toBe('—')
  })
})

describe('倒计时', () => {
  const now = new Date('2026-10-17T12:00:00').getTime()

  it('超过一天时带天数', () => {
    expect(formatRemaining('2026-10-18T14:03:04', now)).toBe('1天02:03:04')
  })

  it('不足一天时只显示时分秒', () => {
    expect(formatRemaining('2026-10-17T12:03:04', now)).toBe('00:03:04')
  })

  it('已过期显示「已结束」', () => {
    expect(formatRemaining('2026-10-17T11:59:59', now)).toBe('已结束')
  })
})

describe('已抢进度', () => {
  it('按 总数-剩余 计算百分比', () => {
    expect(stockPercent(25, 100)).toBe(75)
    expect(stockPercent(0, 100)).toBe(100)
    expect(stockPercent(100, 100)).toBe(0)
  })

  it('库存未知或总数为 0 时返回 0，避免除零', () => {
    expect(stockPercent(null, 100)).toBe(0)
    expect(stockPercent(10, 0)).toBe(0)
    expect(stockPercent(10, null)).toBe(0)
  })
})
