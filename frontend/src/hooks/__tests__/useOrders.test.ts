import { describe, expect, it } from 'vitest'
import { hasQueuingOrder, isTerminalStatus } from '../queries/useOrders'
import type { OrderListItem } from '../../types/order'

const order = (status: string): OrderListItem => ({
  orderNo: 'SK1',
  activityId: '1',
  productId: '2',
  status,
  createdAt: '2026-10-17T12:00:00',
})

describe('订单终态判定（决定轮询是否停止）', () => {
  // 真实 OrderStatusEnum：QUEUING(0) / CREATED(1) / CREATE_FAILED(2) / CANCELLED(3)
  // 本项目无支付环节 ⇒ CREATED 即最终成功态，必须视为终态，否则会一直轮询。
  it('CREATED / CREATE_FAILED / CANCELLED 是终态', () => {
    expect(isTerminalStatus('CREATED')).toBe(true)
    expect(isTerminalStatus('CREATE_FAILED')).toBe(true)
    expect(isTerminalStatus('CANCELLED')).toBe(true)
  })

  it('QUEUING 不是终态，必须继续轮询', () => {
    expect(isTerminalStatus('QUEUING')).toBe(false)
  })

  it('未知状态视为非终态（宁可多轮询一次，也不要卡在排队）', () => {
    expect(isTerminalStatus(undefined)).toBe(false)
    expect(isTerminalStatus('WEIRD')).toBe(false)
  })

  it('列表里还有排队中订单时才继续轮询', () => {
    expect(hasQueuingOrder([order('CREATED'), order('QUEUING')])).toBe(true)
    expect(hasQueuingOrder([order('CREATED'), order('CREATE_FAILED')])).toBe(false)
    expect(hasQueuingOrder([])).toBe(false)
    expect(hasQueuingOrder(undefined)).toBe(false)
  })
})
