/**
 * 主键归一化。
 *
 * 事实（已实测）：后端 `Long` 主键经 Jackson 序列化后，**只有 ≥16 位的数字**才会被
 * 前端的 `http` 层转成字符串（防 JS 精度丢失）；13~15 位的 ID 仍然是 JSON number。
 * 例如活动 `90295171892224`、商品 `90173199893504`。
 *
 * 而 TS 类型把 `Id` 声明为 `string`，于是运行时出现三类真实故障：
 * 1. `setActivityId(item.activityId)` 后调用 `.trim()` → `TypeError: trim is not a function`；
 * 2. `item.productId === productIdFromUrl` → 数字与字符串比较恒为 false（商品详情看不到活动）；
 * 3. queryKey 一会儿是数字、一会儿是字符串 → 同一资源两份缓存、失效不生效。
 *
 * 因此所有 ID 在 **API 边界**统一归一为字符串，后续代码可以放心按 `string` 处理。
 */
export type RawId = string | number | null | undefined

export function toId(value: RawId): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 可选 ID：保留 `undefined` 语义（用于订单详情里可能缺失的字段）。 */
export function toOptionalId(value: RawId): string | undefined {
  return value === null || value === undefined ? undefined : String(value)
}
