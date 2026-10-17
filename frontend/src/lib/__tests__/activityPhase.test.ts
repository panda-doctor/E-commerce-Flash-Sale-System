import { describe, expect, it } from 'vitest'
import { PHASE_TEXT, phaseOfItem, phaseHint, resolvePhase } from '../activityPhase'
import type { ActivityItem } from '../../types/activity'

const NOW = new Date('2026-10-17T12:00:00').getTime()

const baseItem: ActivityItem = {
  activityId: '1',
  productId: '2',
  productName: '测试商品',
  productImage: null,
  activityName: '测试活动',
  startTime: '2026-10-17T11:00:00',
  endTime: '2026-10-17T13:00:00',
  seckillPrice: 9900,
  originalPrice: 19900,
  status: 'RUNNING',
  stock: 10,
  preheated: true,
  totalStock: 100,
  limitPerUser: 1,
}

describe('活动阶段状态机', () => {
  it('进行中：已预热 + 有库存 + 时间窗内', () => {
    expect(phaseOfItem(baseItem, NOW)).toBe('IN_PROGRESS')
    expect(PHASE_TEXT.IN_PROGRESS).toBe('进行中')
    expect(phaseHint('IN_PROGRESS')).toBeNull()
  })

  it('未预热优先于售罄：列表用 preheated=false，详情用 stock=null', () => {
    expect(resolvePhase({ ...baseItem, preheated: false, now: NOW })).toBe('PREHEAT_MISSING')
    expect(
      resolvePhase({
        status: 'IN_PROGRESS',
        startTime: baseItem.startTime,
        endTime: baseItem.endTime,
        stock: null,
        now: NOW,
      }),
    ).toBe('PREHEAT_MISSING')
  })

  it('未开始由开始时间判定，即使后端状态滞后', () => {
    expect(
      resolvePhase({
        status: 'RUNNING',
        startTime: '2026-10-17T13:00:00',
        endTime: '2026-10-17T15:00:00',
        stock: 10,
        preheated: true,
        now: NOW,
      }),
    ).toBe('NOT_STARTED')
  })

  it('反向：状态仍是 NOT_STARTED 但已到开始时间 → 进行中（后端状态是快照，不自动翻转）', () => {
    expect(
      resolvePhase({
        status: 'NOT_STARTED',
        startTime: '2026-10-17T11:00:00',
        endTime: '2026-10-17T13:00:00',
        stock: 5,
        preheated: true,
        now: NOW,
      }),
    ).toBe('IN_PROGRESS')
  })

  it('已结束由结束时间或后端状态判定', () => {
    expect(resolvePhase({ ...baseItem, endTime: '2026-10-17T11:30:00', now: NOW })).toBe('ENDED')
    expect(resolvePhase({ ...baseItem, status: 'ENDED', now: NOW })).toBe('ENDED')
  })

  it('后端判定的 SOLD_OUT 直接视为售罄', () => {
    expect(resolvePhase({ ...baseItem, status: 'SOLD_OUT', stock: 3, now: NOW })).toBe('SOLD_OUT')
  })

  it('库存为 0 → 已售罄；取消 → 已取消', () => {
    expect(resolvePhase({ ...baseItem, stock: 0, now: NOW })).toBe('SOLD_OUT')
    expect(resolvePhase({ ...baseItem, status: 'CANCELLED', now: NOW })).toBe('CANCELLED')
  })

  it('不可抢购阶段都给出原因', () => {
    expect(phaseHint('PREHEAT_MISSING')).toContain('预热')
    expect(phaseHint('NOT_STARTED')).toContain('还未开始')
    expect(phaseHint('SOLD_OUT')).toContain('抢光')
    expect(phaseHint('ENDED')).toContain('已结束')
    expect(phaseHint('CANCELLED')).toContain('已取消')
  })
})
