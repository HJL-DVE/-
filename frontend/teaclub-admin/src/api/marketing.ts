import { http } from './request'

/** ============ 钀ラ攢锛氫紭鎯犲埜妯℃澘 ============ */

export interface CouponTemplateVO {
  id: number
  templateNo: string
  couponName: string
  couponType: string
  couponTypeLabel?: string
  scopeType: string
  faceValue?: number | null
  discountRate?: number | null
  maxDiscountAmount?: number | null
  thresholdAmount?: number | null
  totalCount?: number | null
  issuedCount?: number | null
  perMemberLimit?: number | null
  validType?: string
  validStart?: string | null
  validEnd?: string | null
  validDays?: number | null
  newMemberOnly?: number
  birthdayOnly?: number
  status: string
  remark?: string | null
  createTime?: string | null
  storeIds?: number[]
  spuIds?: number[]
  levelIds?: number[]
}

export interface CouponVO {
  id: number
  couponNo: string
  templateId: number
  couponName?: string | null
  couponType: string
  couponTypeLabel?: string
  faceValue?: number | null
  discountRate?: number | null
  thresholdAmount?: number | null
  status: string
  statusLabel?: string
  obtainType?: string
  obtainTime?: string | null
  validStart?: string | null
  validEnd?: string | null
  lockedOrderNo?: string | null
  usedOrderNo?: string | null
  discountAmount?: number | null
  usable?: boolean
}

export function getCouponTemplates(query: {
  pageNo?: number
  pageSize?: number
  couponType?: string
  status?: string
}) {
  return http.get<{ records: CouponTemplateVO[]; total: number }>(
    '/api/admin/marketing/coupons/templates',
    query,
  )
}

export function getCouponTemplate(id: number) {
  return http.get<CouponTemplateVO>('/api/admin/marketing/coupons/templates/' + id)
}

/** 收银台：查询会员可用于本单的优惠券 */
export function getUsableCouponsForOrder(params: {
  memberId: number
  orderAmount?: number
  storeId?: number
}) {
  return http.get<CouponVO[]>('/api/admin/marketing/coupons/usable-for-order', params)
}

export function createCouponTemplate(data: Partial<CouponTemplateVO>) {
  return http.post<number>('/api/admin/marketing/coupons/templates', data)
}

export function updateCouponTemplate(id: number, data: Partial<CouponTemplateVO>) {
  return http.put<void>('/api/admin/marketing/coupons/templates/' + id, data)
}

export function deleteCouponTemplate(id: number) {
  return http.del<void>('/api/admin/marketing/coupons/templates/' + id)
}

export function changeCouponTemplateStatus(id: number, status: string) {
  return http.put<void>(
    '/api/admin/marketing/coupons/templates/' + id + '/status?status=' + status,
    undefined,
  )
}

export function getMemberCoupons(query: { memberId: number; status?: string; limit?: number }) {
  return http.get<CouponVO[]>('/api/admin/marketing/coupons/instances', query)
}

export function grantCoupon(templateId: number, memberId: number, qty = 1) {
  return http.post<CouponVO[]>(
    '/api/admin/marketing/coupons/templates/' + templateId + '/grant',
    undefined,
    { params: { memberId, qty } },
  )
}

/** ============ 钀ラ攢锛氱Н鍒?============ */

export interface PointsAccountVO {
  memberId: number
  totalPoints: number
  totalEarned: number
  totalConsumed: number
  totalReversed: number
  updateTime?: string | null
}

export interface PointsTransactionVO {
  id: number
  txnNo: string
  bizType: string
  bizTypeLabel?: string
  bizNo?: string | null
  changePoints: number
  beforePoints: number
  afterPoints: number
  remark?: string | null
  createTime?: string | null
}

export function getPointsAccount(memberId: number) {
  return http.get<PointsAccountVO>('/api/admin/marketing/points/account', { memberId })
}

export function getPointsTransactions(query: {
  memberId: number
  bizType?: string
  limit?: number
}) {
  return http.get<PointsTransactionVO[]>('/api/admin/marketing/points/transactions', query)
}

export function adjustPoints(query: {
  memberId: number
  points: number
  bizNo?: string
  remark?: string
}) {
  return http.post<boolean>('/api/admin/marketing/points/adjust', undefined, { params: query })
}

/** 积分规则（读真实 mkt_points_rule 表） */
export interface PointsRuleVO {
  /** 规则 ID（编辑时使用） */
  id?: number
  ruleCode: string
  ruleName: string
  amountPerPoint: number
  signInPoints: number
  signInContinuousMax: number
  signInBonus: number
  expireDays: number
  status: string
  usedBy: string
}

export function getPointsRules() {
  return http.get<PointsRuleVO[]>('/api/admin/marketing/points/rules')
}

/** PUT /api/admin/marketing/points/rules/{id} —— 更新积分规则（ruleCode 不可改） */
export function updatePointsRule(id: number, payload: {
  amountPerPoint?: number
  signInPoints?: number
  signInContinuousMax?: number
  signInBonus?: number
  expireDays?: number
  status?: string
}) {
  return http.put<PointsRuleVO>(`/api/admin/marketing/points/rules/${id}`, payload)
}

/** ============ 钀ラ攢锛氭椿鍔?============ */

export interface ActivityVO {
  id: number
  activityNo: string
  activityName: string
  activityType: string
  activityTypeLabel?: string
  scopeType: string
  startTime?: string | null
  endTime?: string | null
  configJson?: string | null
  totalQuota?: number | null
  usedQuota?: number | null
  perMemberLimit?: number | null
  priority?: number | null
  status: string
  statusLabel?: string
  description?: string | null
  createTime?: string | null
  storeIds?: number[]
}

export function getActivities(query?: { activityType?: string; status?: string }) {
  return http.get<ActivityVO[]>('/api/admin/marketing/activities', query)
}

export function getActivity(id: number) {
  return http.get<ActivityVO>('/api/admin/marketing/activities/' + id)
}

export function createActivity(data: Record<string, unknown>) {
  return http.post<number>('/api/admin/marketing/activities', data)
}

export function updateActivity(id: number, data: Record<string, unknown>) {
  return http.put<void>('/api/admin/marketing/activities/' + id, data)
}

export function deleteActivity(id: number) {
  return http.del<void>('/api/admin/marketing/activities/' + id)
}

export function changeActivityStatus(id: number, status: string) {
  return http.put<void>(
    '/api/admin/marketing/activities/' + id + '/status?status=' + status,
    undefined,
  )
}

/** ============ 钀ラ攢锛氱Н鍒嗗晢鍩?============ */

export interface PointsGoodsVO {
  id: number
  goodsNo: string
  goodsName: string
  goodsType: string
  imageUrl?: string | null
  pointsPrice: number
  stock: number
  templateId?: number | null
  exchangeLimit?: number | null
  sort?: number | null
  status: string
  createTime?: string | null
}

export function getPointsGoods(query?: { status?: string }) {
  return http.get<PointsGoodsVO[]>('/api/admin/marketing/points-goods', query)
}

export function createPointsGoods(data: Partial<PointsGoodsVO>) {
  return http.post<number>('/api/admin/marketing/points-goods', data)
}

export function updatePointsGoods(id: number, data: Partial<PointsGoodsVO>) {
  return http.put<void>('/api/admin/marketing/points-goods/' + id, data)
}

export function deletePointsGoods(id: number) {
  return http.del<void>('/api/admin/marketing/points-goods/' + id)
}

/** ============ 钀ラ攢锛氱瓑绾ф潈鐩?============ */

export interface LevelBenefitVO {
  id?: number
  levelId: number
  benefitCode: string
  benefitName: string
  benefitValue?: string | null
  status?: string
}

export function getLevelBenefits() {
  return http.get<Record<string, LevelBenefitVO[]>>('/api/admin/marketing/level-benefits')
}

export function getLevelBenefitsByLevel(levelId: number) {
  return http.get<LevelBenefitVO[]>('/api/admin/marketing/level-benefits/level/' + levelId)
}

export function saveLevelBenefit(data: LevelBenefitVO) {
  return http.post<number>('/api/admin/marketing/level-benefits', data)
}

export function deleteLevelBenefit(id: number) {
  return http.del<void>('/api/admin/marketing/level-benefits/' + id)
}
