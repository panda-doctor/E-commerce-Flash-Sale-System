import { http } from '../lib/http'
import { toId } from '../lib/id'
import type { PageResponse } from '../types/api'
import type { Product, ProductQuery } from '../types/product'

/** 后端 13~15 位主键仍是 JSON number，入口处统一转字符串（见 lib/id.ts）。 */
function normalizeProduct(product: Product): Product {
  return { ...product, productId: toId(product.productId) }
}

/** GET /api/products?page&pageSize&keyword（pageSize 上限 100，后端只返回上架商品）。 */
export function fetchProducts(query: ProductQuery = {}) {
  const params = new URLSearchParams()
  params.set('page', String(query.page ?? 1))
  params.set('pageSize', String(query.pageSize ?? 12))
  if (query.keyword?.trim()) {
    params.set('keyword', query.keyword.trim())
  }
  return http
    .get<PageResponse<Product>>(`/api/products?${params.toString()}`)
    .then((page) => ({ ...page, records: page.records.map(normalizeProduct) }))
}

/** GET /api/products/{productId}（不存在/已下架 → 非 0 code）。 */
export function fetchProduct(productId: string) {
  return http
    .get<Product>(`/api/products/${encodeURIComponent(productId)}`)
    .then(normalizeProduct)
}
