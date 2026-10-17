import { useQuery } from '@tanstack/react-query'
import { fetchProduct, fetchProducts } from '../../api/product'
import { queryKeys } from '../../lib/queryKeys'
import type { ProductQuery } from '../../types/product'

/** 商品列表：翻页时保留上一页数据，避免列表跳动。 */
export function useProducts(query: ProductQuery) {
  return useQuery({
    queryKey: queryKeys.products(query),
    queryFn: () => fetchProducts(query),
    staleTime: 60_000,
    // 只在"翻页"时保留上一页数据；换了关键词就清空，避免展示与当前条件不符的旧结果
    placeholderData: (previousData, previousQuery) => {
      const previous = previousQuery?.queryKey[1] as ProductQuery | undefined
      return previous && previous.keyword === query.keyword ? previousData : undefined
    },
  })
}

export function useProduct(productId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.product(productId ?? 'none'),
    queryFn: () => fetchProduct(productId as string),
    enabled: Boolean(productId),
    staleTime: 60_000,
  })
}
