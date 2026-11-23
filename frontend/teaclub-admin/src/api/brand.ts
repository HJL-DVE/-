import { http } from './request'

export interface BrandVO {
  id: number
  brandCode: string
  brandName: string
  logoUrl?: string | null
  contactPhone?: string | null
  walletDeductOrder?: string | null
  /** 退款是否需要人工审核：1 需要 0 自助秒退 */
  refundNeedAudit?: number | null
  /** 同一会员每日退卡次数上限，0 表示不限 */
  refundDailyLimit?: number | null
  /** 赠送金已被消费时的处置：REJECT / ALLOW */
  refundGiftConsumedAction?: string | null
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
/**
 * PUT /api/admin/store/brand/refund-rule —— 更新退款规则。
 *
 * 独立于品牌基础信息维护，仅「老板」权限（brand:refund-rule:update）可调用。
 */
export function updateRefundRule(data: {
  walletDeductOrder?: string
  refundNeedAudit?: number
  refundDailyLimit?: number
  refundGiftConsumedAction?: string
}) {
  return http.put<void>('/api/admin/store/brand/refund-rule', data)
}