import { http } from './request'
import type { PageResult, PayCreateRequest, PayCreateVO, PaymentVO, RefundApplyRequest, RefundVO } from './types'

export function createPayment(data: PayCreateRequest): Promise<PayCreateVO> {
  return http.post<PayCreateVO>('/api/admin/payment/create', data)
}

export function queryPayment(paymentNo: string, silent = true): Promise<PaymentVO> {
  return http.get<PaymentVO>('/api/admin/payment/orders/' + paymentNo + '/query', undefined, { silent })
}

export function getPaymentPage(query: {
  pageNo?: number
  pageSize?: number
  status?: string
  bizType?: string
}): Promise<PageResult<PaymentVO>> {
  return http.get<PageResult<PaymentVO>>('/api/admin/payment/orders', query)
}

export function applyRefund(data: RefundApplyRequest): Promise<RefundVO> {
  return http.post<RefundVO>('/api/admin/payment/refunds', data)
}

export function getRefundPage(query: {
  pageNo?: number
  pageSize?: number
  status?: string
}): Promise<PageResult<RefundVO>> {
  return http.get<PageResult<RefundVO>>('/api/admin/payment/refunds', query)
}

export function getNotifyLogPage(query: {
  pageNo?: number
  pageSize?: number
  status?: string
}): Promise<PageResult<Record<string, unknown>>> {
  return http.get<PageResult<Record<string, unknown>>>('/api/admin/payment/notify-logs', query)
}
