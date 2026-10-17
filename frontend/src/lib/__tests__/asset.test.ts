import { describe, expect, it } from 'vitest'
import { toSameOriginPath } from '../asset'

/**
 * 联调实测：上传接口返回 `http://172.21.112.1:8084/uploads/...`（内网绝对地址）。
 * 这里固化归一化规则，避免"本机看着正常、换台机器图片全挂"。
 */
describe('静态资源地址归一化', () => {
  it('回环地址 → 同源路径', () => {
    expect(toSameOriginPath('http://127.0.0.1:8084/uploads/202610/a.png')).toBe('/uploads/202610/a.png')
    expect(toSameOriginPath('http://localhost:8084/uploads/x.jpg')).toBe('/uploads/x.jpg')
  })

  it('私网 IP（172.16/192.168/10.x）→ 同源路径', () => {
    expect(toSameOriginPath('http://172.21.112.1:8084/uploads/a.png')).toBe('/uploads/a.png')
    expect(toSameOriginPath('http://192.168.1.20/uploads/a.png')).toBe('/uploads/a.png')
    expect(toSameOriginPath('http://10.0.0.5:9000/uploads/a.png')).toBe('/uploads/a.png')
  })

  it('商品服务端口 8084 的绝对地址 → 同源路径', () => {
    expect(toSameOriginPath('http://ecommerce.internal:8084/uploads/a.png')).toBe('/uploads/a.png')
  })

  it('外链 CDN / OSS 保持原样（不能误伤）', () => {
    const oss = 'https://flash-sale.oss-cn-hangzhou.aliyuncs.com/uploads/a.png'
    expect(toSameOriginPath(oss)).toBe(oss)
    expect(toSameOriginPath('https://cdn.example.com/img/a.png')).toBe('https://cdn.example.com/img/a.png')
  })

  it('已是相对路径则原样返回；空值返回 null', () => {
    expect(toSameOriginPath('/uploads/a.png')).toBe('/uploads/a.png')
    expect(toSameOriginPath('   ')).toBeNull()
    expect(toSameOriginPath(null)).toBeNull()
    expect(toSameOriginPath(undefined)).toBeNull()
  })
})
