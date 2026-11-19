import { http } from '@/utils/request'
import type { PageResult, RechargeOrder, RechargePlan, WalletAccount, WalletTransaction } from './types'

/** GET /api/wallet/account */
export function getWalletAccount() {
  return http.get<WalletAccount>('/api/wallet/account')
}

/** GET /api/wallet/transactions?pageNo=&pageSize=&bizType= */
export function getTransactions(params: { pageNo?: number; pageSize?: number; bizType?: string }) {
  return http.get<PageResult<WalletTransaction>>('/api/wallet/transactions', params)
}

/** GET /api/wallet/plans?storeId= */
export function getRechargePlans(storeId?: number | string) {
  return http.get<RechargePlan[]>('/api/wallet/plans', { storeId })
}

/**
 * POST /api/wallet/recharges
 * 契约已改为 planId + storeId 均必传；入账只能由支付回调驱动，前端不本地加余额。
 */
export function createRecharge(payload: { planId: number; storeId: number | string }) {
  return http.post<RechargeOrder>('/api/wallet/recharges', payload)
}

/** GET /api/wallet/recharges/{rechargeNo} —— 到账轮询（status: PENDING/PAID/CANCELLED） */
export function getRechargeOrder(rechargeNo: string) {
  return http.get<RechargeOrder>('/api/wallet/recharges/' + rechargeNo)
}

/** GET /api/wallet/recharges?pageNo= */
export function getRechargeOrders(params: { pageNo?: number; pageSize?: number }) {
  return http.get<PageResult<RechargeOrder>>('/api/wallet/recharges', params)
}
