import { http } from '@/utils/request'

/** ============ 优惠券 ============ */

export interface CouponItem {
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
  validStart?: string | null
  validEnd?: string | null
  obtainTime?: string | null
  usable?: boolean
}

/** GET /api/marketing/coupons/mine?status=&limit= */
export function getMyCoupons(params?: { status?: string; limit?: number }) {
  return http.get<CouponItem[]>('/api/marketing/coupons/mine', params)
}

/** 可领取的券模板（优惠券中心） */
export interface CouponTemplateItem {
  id: number
  templateNo: string
  couponName: string
  couponType: string
  couponTypeLabel?: string
  faceValue?: number | null
  discountRate?: number | null
  thresholdAmount?: number | null
  perMemberLimit?: number | null
  validDays?: number | null
  birthdayOnly?: number
  newMemberOnly?: number
  receivedCount?: number | null
  remark?: string | null
}

/** GET /api/marketing/coupons/available?birthdayOnly=&newMemberOnly= */
export function getAvailableCoupons(params?: {
  birthdayOnly?: boolean
  newMemberOnly?: boolean
}) {
  return http.get<CouponTemplateItem[]>('/api/marketing/coupons/available', params)
}

/** GET /api/marketing/coupons/usable */
export function getUsableCoupons() {
  return http.get<CouponItem[]>('/api/marketing/coupons/usable')
}

/**
 * POST /api/marketing/coupons/receive?templateId=&qty=
 *
 * <p>小程序的 http.post 不支持 query 选项，参数必须拼在 URL 上。
 */
export function receiveCoupon(templateId: number, qty = 1) {
  return http.post<CouponItem[]>(
    '/api/marketing/coupons/receive?templateId=' + templateId + '&qty=' + qty,
  )
}

/** ============ 积分 ============ */

export interface PointsAccount {
  memberId: number
  totalPoints: number
  totalEarned: number
  totalConsumed: number
  totalReversed: number
}

export interface PointsTxn {
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

export interface SignInResult {
  points: number
  continuousDays: number
  signDate: string
  firstTime: boolean
  totalPoints: number
  bonusRemark?: string | null
}

/** GET /api/marketing/points/account */
export function getPointsAccount() {
  return http.get<PointsAccount>('/api/marketing/points/account')
}

/** GET /api/marketing/points/transactions?bizType=&limit= */
export function getPointsTransactions(params?: { bizType?: string; limit?: number }) {
  return http.get<PointsTxn[]>('/api/marketing/points/transactions', params)
}

/** POST /api/marketing/points/sign-in */
export function signIn() {
  return http.post<SignInResult>('/api/marketing/points/sign-in')
}

/** ============ 积分商城 ============ */

export interface PointsGoods {
  id: number
  goodsNo: string
  goodsName: string
  goodsType: string
  imageUrl?: string | null
  pointsPrice: number
  stock: number
  exchangeLimit?: number | null
  status: string
}

export interface ExchangeResult {
  exchangeNo: string
  goodsName: string
  pointsCost: number
  remainPoints: number
  couponNo?: string | null
  createTime?: string | null
}

/** GET /api/marketing/points-goods */
export function getPointsGoods() {
  return http.get<PointsGoods[]>('/api/marketing/points-goods')
}

/** POST /api/marketing/points-goods/{id}/exchange */
export function exchangeGoods(id: number) {
  return http.post<ExchangeResult>('/api/marketing/points-goods/' + id + '/exchange')
}

/** GET /api/marketing/points-goods/exchanges?limit= */
export function getMyExchanges(limit = 50) {
  return http.get<ExchangeResult[]>('/api/marketing/points-goods/exchanges', { limit })
}

/** ============ 活动 ============ */

export interface ActivityItem {
  id: number
  activityNo: string
  activityName: string
  activityType: string
  activityTypeLabel?: string
  scopeType: string
  startTime?: string | null
  endTime?: string | null
  configJson?: string | null
  description?: string | null
  status: string
  statusLabel?: string
  joined?: boolean
  remainTimes?: number
}

/** GET /api/marketing/activities?storeId= */
export function getActivities(storeId?: number | string) {
  return http.get<ActivityItem[]>('/api/marketing/activities', { storeId })
}

/** POST /api/marketing/activities/{id}/join?storeId= */
export function joinActivity(id: number, storeId?: number | string) {
  const q = storeId === undefined || storeId === null ? '' : '?storeId=' + storeId
  return http.post<boolean>('/api/marketing/activities/' + id + '/join' + q)
}

/** ============ 等级权益 ============ */

export interface LevelBenefit {
  id: number
  levelId: number
  benefitCode: string
  benefitName: string
  benefitValue?: string | null
  status?: string
}

/** GET /api/marketing/level-benefits/level/{levelId} */
export function getLevelBenefits(levelId: number) {
  return http.get<LevelBenefit[]>('/api/marketing/level-benefits/level/' + levelId)
}
