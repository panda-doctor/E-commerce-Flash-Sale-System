import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchProduct, fetchProducts } from '../../api/product'
import type { ProductQuery } from '../../types/product'

/** 商品列表：翻页时保留上一页数据，避免列表跳动。 */
export function useProducts(query: ProductQuery) {
  return useQuery({
    queryKey: ['products', query],
    queryFn: () => fetchProducts(query),
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  })
}

export function useProduct(productId: string | undefined) {
  return useQuery({
    queryKey: ['product', productId],
    queryFn: () => fetchProduct(productId as string),
    enabled: Boolean(productId),
    staleTime: 60_000,
  })
}
