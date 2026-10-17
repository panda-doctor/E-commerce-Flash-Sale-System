import { describe, expect, it } from 'vitest'
import { nextPollDelay } from '../useOrders'

const base = {
  visible: true,
  status: 'QUEUING' as const,
  failureCount: 0,
  elapsedMs: 0,
  intervalMs: 1500,
  maxDurationMs: 90_000,
  maxFailures: 5,
}

describe('轮询决策 nextPollDelay（决定是否继续拉取）', () => {
  it('正常排队中：按间隔继续', () => {
    expect(nextPollDelay(base)).toBe(1500)
  })

  it('页面切到后台：立即停止（不再产生无意义请求）', () => {
    expect(nextPollDelay({ ...base, visible: false })).toBe(false)
  })

  it('进入终态：停止', () => {
    expect(nextPollDelay({ ...base, status: 'CREATED' })).toBe(false)
    expect(nextPollDelay({ ...base, status: 'CREATE_FAILED' })).toBe(false)
    expect(nextPollDelay({ ...base, status: 'CANCELLED' })).toBe(false)
  })

  it('连续失败达到上限：熔断停止（原先会无限重试）', () => {
    expect(nextPollDelay({ ...base, failureCount: 4 })).toBe(1500)
    expect(nextPollDelay({ ...base, failureCount: 5 })).toBe(false)
  })

  it('超过时长上限：停止（原先会无限轮询）', () => {
    expect(nextPollDelay({ ...base, elapsedMs: 89_999 })).toBe(1500)
    expect(nextPollDelay({ ...base, elapsedMs: 90_000 })).toBe(false)
  })

  it('首帧还没有数据：不视为终态，继续请求', () => {
    expect(nextPollDelay({ ...base, status: undefined })).toBe(1500)
  })
})
