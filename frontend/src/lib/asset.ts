/**
 * 静态资源地址归一化。
 *
 * 背景（联调实测）：`POST /api/admin/files/image` 返回的 `url` 是**绝对地址**，
 * 形如 `http://172.21.112.1:8084/uploads/202610/xxx.png` —— 它指向商品服务的
 * 内网地址与端口。若前端直接把它塞进 `<img src>`：
 * - 浏览器会跨站直连 8084，绕开了同源代理（与"一切经网关"的部署约定冲突）；
 * - 内网 IP 在别人的机器/外网环境根本不可达 ⇒ 图片全挂。
 *
 * 因此对"明确属于本地存储服务"的地址（回环/私网 IP，或 8084 端口）取路径部分，
 * 变成同源 `/uploads/...`，由 Vite 代理或生产环境反代转发；其它外链（如 OSS/CDN）保持原样。
 */

const PRIVATE_HOST = /^(localhost|127\.\d+\.\d+\.\d+|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+|\[::1\])$/i

export function toSameOriginPath(url: string | null | undefined): string | null {
  if (url === null || url === undefined) {
    return null
  }
  const trimmed = url.trim()
  if (!trimmed) {
    return null
  }
  // 已经是相对路径（同源）：直接使用
  if (trimmed.startsWith('/')) {
    return trimmed
  }

  const match = /^(https?):\/\/([^/]+)(\/.*)?$/i.exec(trimmed)
  if (!match) {
    return trimmed
  }

  const path = match[3] ?? '/'
  if (!path.startsWith('/uploads/')) {
    return trimmed
  }

  const hostPort = match[2]
  const [host, port] = hostPort.split(':')
  const isLocalStorage = PRIVATE_HOST.test(host) || port === '8084'

  return isLocalStorage ? path : trimmed
}
