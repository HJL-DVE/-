import { http } from './request'
import type { PageResult, StoreQuery, StoreVO } from './types'

export interface StoreSavePayload {
  storeCode: string
  storeName: string
  province?: string
  city?: string
  district?: string
  address?: string
  phone?: string
  managerName?: string
  businessHours?: string
  openDate?: string
  status?: string
  remark?: string
}

export function getAccessibleStores(): Promise<StoreVO[]> {
  return http.get<StoreVO[]>('/api/admin/store/stores/accessible')
}

export function getStorePage(query: Partial<StoreQuery>): Promise<PageResult<StoreVO>> {
  return http.get<PageResult<StoreVO>>('/api/admin/store/stores', query)
}

export function getStoreDetail(id: number): Promise<StoreVO> {
  return http.get<StoreVO>('/api/admin/store/stores/' + id)
}

export function createStore(data: StoreSavePayload): Promise<number> {
  return http.post<number>('/api/admin/store/stores', data)
}

export function updateStore(id: number, data: StoreSavePayload): Promise<void> {
  return http.put<void>('/api/admin/store/stores/' + id, data)
}

export function deleteStore(id: number): Promise<void> {
  return http.del<void>('/api/admin/store/stores/' + id)
}

export function updateStoreStatus(id: number, status: string): Promise<void> {
  return http.patch<void>('/api/admin/store/stores/' + id + '/status', { status })
}
