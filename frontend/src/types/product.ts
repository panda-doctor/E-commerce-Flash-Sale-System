import type { Id } from './api'

/** `ProductStatusEnum`（已按真实代码核对）：`OFF_SHELF(0,"下架")` / `ON_SHELF(1,"上架")`。 */
export type ProductStatus = 'ON_SHELF' | 'OFF_SHELF' | string

/** `ProductVO`（价格单位为「分」）。 */
export interface Product {
  productId: Id
  name: string
  description: string | null
  imageUrl: string | null
  originalPrice: number | null
  totalStock: number | null
  availableStock: number | null
  status: ProductStatus
  cacheHit?: boolean
}

export interface ProductQuery {
  page?: number
  pageSize?: number
  keyword?: string
}
