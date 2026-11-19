import { http } from './request'
import type {
  PageResult,
  RechargeOrderVO,
  RechargePlanVO,
  RechargeQuery,
  TransactionQuery,
  WalletStatsVO,
  WalletTransactionVO,
  WalletVO,
  WalletQuery,
} from './types'

// 列表类读接口统一 silent：页面用 ApiPending 内联展示"接口未就绪"，避免重复弹窗
const SILENT = { silent: true }

export function getWalletPage(query: Partial<WalletQuery>) {
  return http.get<PageResult<WalletVO>>('/api/admin/wallet/accounts', query, SILENT)
}

export function getWalletByMember(memberId: number) {
  return http.get<WalletVO>('/api/admin/wallet/accounts/member/' + memberId, undefined, SILENT)
}

export function openWallet(memberId: number) {
  return http.post<Record<string, unknown>>(
    '/api/admin/wallet/accounts/member/' + memberId + '/open',
    undefined,
    SILENT,
  )
}

export function getTransactionPage(query: Partial<TransactionQuery>) {
  return http.get<PageResult<WalletTransactionVO>>('/api/admin/wallet/transactions', query, SILENT)
}

export function getMemberTransactions(memberId: number, limit = 20) {
  return http.get<WalletTransactionVO[]>('/api/admin/wallet/transactions/member/' + memberId, { limit }, SILENT)
}

export function getReconcile() {
  return http.get<Record<string, unknown>>('/api/admin/wallet/reconcile', undefined, SILENT)
}

export function getRechargePage(query: Partial<RechargeQuery>) {
  return http.get<PageResult<RechargeOrderVO>>('/api/admin/wallet/recharges', query, SILENT)
}

export function createRecharge(data: {
  memberId: number
  storeId: number
  planId?: number
  customAmount?: number
  payChannel?: string
  remark?: string
}) {
  return http.post<RechargeOrderVO>('/api/admin/wallet/recharges', data)
}

export function getRechargeDetail(rechargeNo: string) {
  return http.get<RechargeOrderVO>('/api/admin/wallet/recharges/' + rechargeNo, undefined, SILENT)
}

export function cancelRecharge(rechargeNo: string, reason?: string) {
  return http.post<void>('/api/admin/wallet/recharges/' + rechargeNo + '/cancel', { reason })
}

export function getPlans(status?: string) {
  return http.get<RechargePlanVO[]>('/api/admin/wallet/plans', status ? { status } : undefined, SILENT)
}

export function getAvailablePlans(params: { storeId?: number; memberId?: number }) {
  return http.get<RechargePlanVO[]>('/api/admin/wallet/plans/available', params, SILENT)
}

export function createPlan(data: Partial<RechargePlanVO>) {
  return http.post<number>('/api/admin/wallet/plans', data)
}

export function updatePlan(id: number, data: Partial<RechargePlanVO>) {
  return http.put<void>('/api/admin/wallet/plans/' + id, data)
}

export function deletePlan(id: number) {
  return http.del<void>('/api/admin/wallet/plans/' + id)
}

export function getWalletStats(silent = false) {
  return http.get<WalletStatsVO>('/api/admin/wallet/stats', undefined, { silent })
}
