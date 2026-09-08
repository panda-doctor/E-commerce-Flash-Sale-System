// 展示格式化工具

// 金额：分 -> 元字符串（如 9900 -> '99.00'）
export function fenToYuan(fen) {
  const n = Number(fen)
  if (Number.isNaN(n)) return '--'
  return (n / 100).toFixed(2)
}

// 服务端时间串 'yyyy-MM-dd HH:mm:ss' -> 本地时间戳（毫秒）
// 兼容 Safari：'yyyy-MM-dd HH:mm:ss' 直接 new Date 解析不稳定，统一替换为斜杠按本地时区解析
export function parseServerTime(s) {
  if (s == null) return NaN
  const t = new Date(String(s).replace(/-/g, '/'))
  return t.getTime()
}

const pad = (n, w = 2) => String(n).padStart(w, '0')

// 毫秒 -> 'HH:MM:SS'（小时可超 24）；为负数返回 '00:00:00'
export function formatDuration(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return '00:00:00'
  const total = Math.floor(ms / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

// 秒杀活动状态徽章映射（与后端 ActivityStatusEnum.name 对齐）
export const STATUS_TEXT = {
  NOT_STARTED: '未开始',
  RUNNING: '抢购中',
  ENDED: '已结束',
  SOLD_OUT: '已售罄',
  CANCELLED: '已取消',
}

// check 返回的 reason -> 提示文案
export const CHECK_REASON_TEXT = {
  ALLOW: '可以参与',
  ACTIVITY_NOT_STARTED: '活动尚未开始',
  ACTIVITY_ENDED: '活动已结束',
  ACTIVITY_SOLD_OUT: '手慢了，已抢光',
  ACTIVITY_CANCELLED: '活动已取消',
}

// 订单状态接口：order.status（QUEUING/CREATED）
export const ORDER_STATUS_TEXT = {
  QUEUING: '排队中',
  CREATED: '已创建',
}
