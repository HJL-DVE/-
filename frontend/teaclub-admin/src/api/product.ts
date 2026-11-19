import { http } from './request'
import type { CategoryVO, PageResult, SpuQuery, SpuVO, StoreSpuVO } from './types'

export function getCategoryTree(): Promise<CategoryVO[]> {
  return http.get<CategoryVO[]>('/api/admin/product/categories')
}

export function getSpuPage(query: Partial<SpuQuery>): Promise<PageResult<SpuVO>> {
  return http.get<PageResult<SpuVO>>('/api/admin/product/spus', query)
}

export function getSpuDetail(id: number): Promise<SpuVO> {
  return http.get<SpuVO>('/api/admin/product/spus/' + id)
}

export function createSpu(data: Record<string, unknown>): Promise<number> {
  return http.post<number>('/api/admin/product/spus', data)
}

export function updateSpu(id: number, data: Record<string, unknown>): Promise<void> {
  return http.put<void>('/api/admin/product/spus/' + id, data)
}

export function deleteSpu(id: number): Promise<void> {
  return http.del<void>('/api/admin/product/spus/' + id)
}

export function updateSpuStatus(id: number, status: string): Promise<void> {
  return http.patch<void>('/api/admin/product/spus/' + id + '/status', { status })
}

export function getStoreProducts(query: {
  storeId: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<StoreSpuVO>> {
  return http.get<PageResult<StoreSpuVO>>('/api/admin/product/store-products', query)
}

export function setStoreSkuPrice(storeId: number, skuId: number, storePrice: number, isOnSale = 1): Promise<void> {
  return http.put<void>('/api/admin/product/store-products/' + storeId + '/sku/' + skuId + '/price', {
    storePrice,
    isOnSale,
  })
}

export function shelfStoreSpu(storeId: number, spuId: number, isOnSale: number): Promise<void> {
  return http.patch<void>('/api/admin/product/store-products/' + storeId + '/spu/' + spuId + '/shelf', { isOnSale })
}

export function removeStoreSpu(storeId: number, spuId: number): Promise<void> {
  return http.del<void>('/api/admin/product/store-products/' + storeId + '/spu/' + spuId)
}
