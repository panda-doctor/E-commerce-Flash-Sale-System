/**
 * 后端统一响应信封与分页结构（依据真实代码：
 * `Result<T>{code,message,data}`、`PageResponse<T>{records,page,pageSize,total,pages}`）。
 */
export interface Result<T> {
  code: number
  message: string
  data: T
  /** 后端链路追踪号（排查线上问题时提供给后端最有价值的信息）。 */
  requestId?: string
  timestamp?: number
}

export interface PageResponse<T> {
  records: T[]
  page: number
  pageSize: number
  total: number
  pages: number
}

/**
 * 后端 `Long` 以 JSON number 返回，JavaScript 安全整数上限为 2^53-1。
 * `http` 层只会把 **16 位以上**的整数字面量转成字符串，13~15 位的 ID 仍是 number
 * （例如活动 `90295171892224`）。因此 `Id` 声明为 string 时，**必须在 API 层归一化**
 * （`api/*.ts` 已统一调用 `lib/id.ts` 的 `toId`），否则会出现
 * `number.trim is not a function`、`===` 比较恒 false、queryKey 数字/字符串两套缓存等问题。
 */
export type Id = string

export interface PageQuery {
  page?: number
  pageSize?: number
}
