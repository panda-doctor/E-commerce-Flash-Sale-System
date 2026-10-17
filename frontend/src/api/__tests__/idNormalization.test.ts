import { describe, expect, it, vi } from 'vitest'
import { fetchActivities } from '../activity'
import { fetchProduct } from '../product'
import { fetchMyOrders, fetchOrder } from '../order'

/**
 * 回归：真实后端对 13~15 位 Long 主键返回 **JSON number**（如 90295171892224），
 * 而前端类型声明为 string。若不在 API 边界归一化，会出现
 * `number.trim is not a function` 与 `===` 恒 false（商品详情看不到活动）。
 */
const json = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

const stub = (data: unknown) => vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(json({ code: 0, message: 'ok', data }))))

describe('API 边界：ID 一律归一化为字符串', () => {
  it('活动列表：数字 activityId / productId → string', async () => {
    stub([
      {
        activityId: 90295171892224,
        productId: 90173199893504,
        activityName: '回归-限时秒杀',
        productName: 'P4D4-ProdA',
        productImage: null,
        startTime: '2026-10-17 20:00:00',
        endTime: '2026-10-17 23:00:00',
        seckillPrice: 1999,
        originalPrice: 39900,
        status: 'RUNNING',
        stock: 2,
        preheated: true,
        totalStock: 3,
        limitPerUser: 1,
      },
    ])

    const [item] = await fetchActivities()
    expect(item.activityId).toBe('90295171892224')
    expect(typeof item.activityId).toBe('string')
    expect(typeof item.productId).toBe('string')
  })

  it('商品详情：数字 productId → string（保证与路由参数可比较）', async () => {
    stub({
      productId: 90173199893504,
      name: 'P4D4-ProdA',
      description: null,
      imageUrl: null,
      originalPrice: 39900,
      totalStock: 20,
      availableStock: 20,
      status: 'ON_SHELF',
    })

    const product = await fetchProduct('90173199893504')
    expect(product.productId).toBe('90173199893504')
    expect(typeof product.productId).toBe('string')
  })

  it('我的订单与订单详情：数字 ID → string，缺失字段保持 undefined', async () => {
    stub([{ orderNo: 'SK1', activityId: 90295171892224, productId: 90173199893504, status: 'CREATED', createdAt: '2026-10-17 22:00:00' }])
    const [order] = await fetchMyOrders()
    expect(typeof order.activityId).toBe('string')
    expect(typeof order.productId).toBe('string')

    stub({ orderNo: 'SK1', activityId: 90295171892224, status: 'CREATED' })
    const detail = await fetchOrder('SK1')
    expect(detail.activityId).toBe('90295171892224')
    expect(detail.productId).toBeUndefined()
  })
})
