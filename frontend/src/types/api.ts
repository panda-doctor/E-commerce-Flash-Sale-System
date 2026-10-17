/**
 * 后端统一响应信封与分页结构（依据真实代码：
 * `Result<T>{code,message,data}`、`PageResponse<T>{records,page,pageSize,total,pages}`）。
 */
export interface Result<T> {
  code: number
  message: string
  data: T
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
 * `http` 层会把 16 位以上的整数字面量转成字符串，因此这里统一用 string 承载 ID，
 * 请求时再 `String(id)` 传回，避免历史大 ID（如 1788829276582000000）精度丢失。
 */
export type Id = string

export interface PageQuery {
  page?: number
  pageSize?: number
}
