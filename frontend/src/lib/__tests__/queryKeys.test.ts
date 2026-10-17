import { describe, expect, it } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { clearUserScopedCache, queryKeys, tokenFingerprint } from '../queryKeys'

describe('查询键：用户数据隔离', () => {
  it('不同用户的订单键必须不同（否则会命中别人的缓存）', () => {
    expect(queryKeys.orders('1001')).not.toEqual(queryKeys.orders('1002'))
    expect(queryKeys.order('1001', 'SK1')).not.toEqual(queryKeys.order('1002', 'SK1'))
  })

  it('同一用户同一活动的键稳定', () => {
    expect(queryKeys.orders('1001', 'A')).toEqual(queryKeys.orders('1001', 'A'))
  })

  it('清缓存只清用户域，不动公开数据', () => {
    const client = new QueryClient()
    client.setQueryData(queryKeys.orders('1001'), [])
    client.setQueryData(queryKeys.order('1001', 'SK1'), {})
    client.setQueryData(queryKeys.products({ page: 1, pageSize: 12, keyword: '' }), { total: 1 })

    clearUserScopedCache(client)

    expect(client.getQueryData(queryKeys.orders('1001'))).toBeUndefined()
    expect(client.getQueryData(queryKeys.order('1001', 'SK1'))).toBeUndefined()
    expect(client.getQueryData(queryKeys.products({ page: 1, pageSize: 12, keyword: '' }))).toEqual({ total: 1 })
  })

  it('令牌指纹不可逆且能区分不同令牌', () => {
    const token = 'super-secret-admin-token'
    const fingerprint = tokenFingerprint(token)
    expect(fingerprint).not.toContain(token)
    expect(tokenFingerprint('another-token')).not.toBe(fingerprint)
    expect(tokenFingerprint(null)).toBe('none')
  })
})
