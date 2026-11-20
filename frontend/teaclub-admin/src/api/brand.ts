import { http } from './request'

export interface BrandVO {
  id: number
  brandCode: string
  brandName: string
  logoUrl?: string | null
  contactPhone?: string | null
  walletCrossStoreEnabled?: number
  walletDeductOrder?: string | null
  giftExpireDays?: number | null
  status?: string
  createTime?: string | null
  updateTime?: string | null
}

/** GET /api/admin/store/brand —— 获取当前品牌（权限 brand:view） */
export function getBrand() {
  return http.get<BrandVO>('/api/admin/store/brand')
}

/** PUT /api/admin/store/brand —— 更新品牌（权限 brand:update） */
export function updateBrand(data: Partial<BrandVO>) {
  return http.put<void>('/api/admin/store/brand', data)
}