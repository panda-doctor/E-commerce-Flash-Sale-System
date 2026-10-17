/** 金额（分）→ 「¥12.00」；后端所有价格均以「分」为单位。 */
export function formatPrice(cents: number | null | undefined): string {
  if (cents === null || cents === undefined || Number.isNaN(cents)) {
    return '—'
  }
  return `¥${(cents / 100).toFixed(2)}`
}

/** 金额（分）→ 纯数字文本（用于大字号展示）。 */
export function formatAmount(cents: number | null | undefined): string {
  if (cents === null || cents === undefined || Number.isNaN(cents)) {
    return '—'
  }
  return (cents / 100).toFixed(2)
}

/** 后端 LocalDateTime 形如 `2026-10-17T13:25:00`，Safari 需要空格分隔的兼容写法。 */
export function parseServerTime(value: string | null | undefined): Date | null {
  if (!value) {
    return null
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

const pad = (value: number) => String(value).padStart(2, '0')

export function formatDateTime(value: string | null | undefined): string {
  const date = parseServerTime(value)
  if (!date) {
    return '—'
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 剩余时间：`1天02:03:04` / `02:03:04` / 已结束。 */
export function formatRemaining(target: string | null | undefined, now = Date.now()): string {
  const date = parseServerTime(target)
  if (!date) {
    return '—'
  }
  const diff = date.getTime() - now
  if (diff <= 0) {
    return '已结束'
  }
  const totalSeconds = Math.floor(diff / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const clock = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
  return days > 0 ? `${days}天${clock}` : clock
}

/** 已抢进度百分比（0~100），库存为空时返回 0。 */
export function stockPercent(stock: number | null | undefined, total: number | null | undefined): number {
  if (!total || total <= 0 || stock === null || stock === undefined) {
    return 0
  }
  const sold = Math.max(0, total - stock)
  return Math.min(100, Math.round((sold / total) * 100))
}
