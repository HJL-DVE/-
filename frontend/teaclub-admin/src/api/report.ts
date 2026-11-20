import { http } from './request'

/** ============ 报表（阶段八）============ */

export interface ReportOverview {
  storeCount: number
  memberCount: number
  todayMemberCount: number
  todaySales: number
  todayOrders: number
  todayRechargeAmount: number
  todayRechargeGiftAmount: number
  todayRechargeCount: number
  todayRefundAmount: number
  todayRefundCount: number
  storeRanking: { list: { storeId: number; storeName: string; sales: number; orders: number; avgOrder: number }[] }
}

export interface TrendPoint { date: string; value: number }

export function getDashboardOverview(storeId?: number) {
  return http.get<ReportOverview>('/api/report/dashboard/overview',
    storeId ? { storeId } : undefined)
}

export function getDashboardTrend(days: number, storeId?: number) {
  return http.get<{
    days: number
    salesTrend: TrendPoint[]
    ordersTrend: TrendPoint[]
    memberGrowth: TrendPoint[]
    rechargeTrend: TrendPoint[]
    refundTrend: TrendPoint[]
  }>('/api/report/dashboard/trend',
    storeId ? { days, storeId } : { days })
}

export function getStoreRanking(days = 30) {
  return http.get<{ list: { storeId: number; storeName: string; sales: number; orders: number; avgOrder: number }[] }>(
    '/api/report/store/ranking', { days })
}

export function getStoreOverview(storeId?: number) {
  return http.get<{
    storeId: number | null
    sales: number
    orders: number
    avgOrder: number
    rechargeAmount: number
    rechargeGiftAmount: number
    refundAmount: number
    todayNewMembers: number
    hotProducts: { name: string; qty: number; amount: number }[]
  }>('/api/report/store/overview', storeId ? { storeId } : undefined)
}

export function getProductRanking(days = 30, metric: 'qty' | 'amount' = 'qty', limit = 10) {
  return http.get<{ metric: string; list: { skuId: number; name: string; qty: number; amount: number }[] }>(
    '/api/report/product/ranking', { days, metric, limit })
}

export function getStoreCompare(days = 30) {
  return http.get<{ days: number; list: { storeId: number; storeName: string; sales: number; orders: number }[] }>(
    '/api/report/store/compare', { days })
}

export function getMemberOverview() {
  return http.get<{
    memberCount: number
    levelDistribution: { name: string; value: number }[]
    consumeRank: { id: number; memberNo: string; nickname: string; totalConsume: number; orderCount: number }[]
  }>('/api/report/member/overview')
}

export function getMemberGrowth(days = 30) {
  return http.get<{ days: number; growth: TrendPoint[] }>('/api/report/member/growth', { days })
}

export function getRechargeOverview(days = 30, storeId?: number) {
  return http.get<{
    days: number
    count: number
    principalAmount: number
    giftAmount: number
    creditedAmount: number
    giftNotCountedAsPayment: boolean
    trend: TrendPoint[]
  }>('/api/report/recharge/overview', storeId ? { days, storeId } : { days })
}

export function getPaymentOverview(days = 30, storeId?: number) {
  return http.get<{
    days: number
    mockPaymentMark: string
    byChannel: { channel: string; count: number; amount: number; share: number }[]
    totalMockPaidAmount: number
    walletConsumeAmount: number
  }>('/api/report/payment/overview', storeId ? { days, storeId } : { days })
}

export function getRefundOverview(days = 30, storeId?: number) {
  return http.get<{ days: number; count: number; amount: number; trend: TrendPoint[] }>(
    '/api/report/refund/overview', storeId ? { days, storeId } : { days })
}

export function getMarketingOverview(days = 30) {
  return http.get<{
    days: number
    couponReceived: number
    couponUsed: number
    couponUseRate: number
    pointsEarned: number
    pointsConsumed: number
    memberLevelDistribution: { name: string; value: number }[]
  }>('/api/report/marketing/overview', { days })
}

/** 金额展示：分 -> 元 */
export function yuan(fen: number | undefined | null): string {
  return (((fen ?? 0) as number) / 100).toFixed(2)
}
/** ============ 阶段八扩展（ hourly/分类/商品趋势 ）============ */

export interface HourlyPoint { hour: string; orders: number; sales: number }

export function getHourlyOrders(storeId?: number, date?: string) {
  const q: Record<string, unknown> = {}
  if (storeId) q.storeId = storeId
  if (date) q.date = date
  return http.get<{ date: string; list: HourlyPoint[] }>('/api/report/store/hourly', q)
}

export function getCategoryRanking(days = 30, metric: 'qty' | 'amount' = 'qty', storeId?: number) {
  const q: Record<string, unknown> = { days, metric }
  if (storeId) q.storeId = storeId
  return http.get<{ metric: string; list: { name: string; qty: number; amount: number }[] }>(
    '/api/report/product/category-ranking', q,
  )
}

export function getProductTrend(days = 30, storeId?: number) {
  const q: Record<string, unknown> = { days }
  if (storeId) q.storeId = storeId
  return http.get<{ days: number; trend: TrendPoint[]; trendAmount: TrendPoint[] }>(
    '/api/report/product/trend', q,
  )
}

/** 加载态三元组：所有报表页统一使用 */
export type LoadState = 'loading' | 'error' | 'ready'