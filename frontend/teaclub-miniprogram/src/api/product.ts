import { http } from '@/utils/request'
import type { CategoryItem, SpuDetail, SpuItem } from './types'

/** GET /api/product/stores/{storeId}/categories */
export function getCategories(storeId: number | string) {
  return http.get<CategoryItem[]>('/api/product/stores/' + storeId + '/categories')
}

/** GET /api/product/stores/{storeId}/spus?categoryId=&keyword= */
export function getSpus(storeId: number | string, params?: { categoryId?: number | string; keyword?: string }) {
  return http.get<SpuItem[] | { records: SpuItem[] }>('/api/product/stores/' + storeId + '/spus', params)
}

/** GET /api/product/stores/{storeId}/spus/{spuId} */
export function getSpuDetail(storeId: number | string, spuId: number | string) {
  return http.get<SpuDetail>('/api/product/stores/' + storeId + '/spus/' + spuId)
}

/** GET /api/product/hot?storeId=&limit=6 */
export function getHotProducts(storeId: number | string, limit = 6) {
  return http.get<SpuItem[]>('/api/product/hot', { storeId, limit })
}
