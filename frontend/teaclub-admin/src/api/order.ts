import { http } from './request'
import type {
  OrderCreateRequest,
  OrderItemRequest,
  OrderQuery,
  OrderQuoteVO,
  OrderStatsVO,
  OrderVO,
  PageResult,
} from './types'

export function quoteOrder(data: {
  storeId: number
  memberId?: number | null
  memberCouponId?: number | null
  usePoints?: number | null
  items: OrderItemRequest[]
}): Promise<OrderQuoteVO> {
  return http.post<OrderQuoteVO>('/api/admin/order/quote', data)
}

export function createOrder(data: OrderCreateRequest): Promise<OrderVO> {
  return http.post<OrderVO>('/api/admin/order/orders', data)
}

export function payByBalance(orderNo: string): Promise<OrderVO> {
  return http.post<OrderVO>('/api/admin/order/orders/' + orderNo + '/pay-balance')
}

export function getOrderPage(query: Partial<OrderQuery>): Promise<PageResult<OrderVO>> {
  return http.get<PageResult<OrderVO>>('/api/admin/order/orders', query)
}

export function getOrderDetail(orderNo: string): Promise<OrderVO> {
  return http.get<OrderVO>('/api/admin/order/orders/' + orderNo)
}

export function cancelOrder(orderNo: string, reason?: string): Promise<void> {
  return http.post<void>('/api/admin/order/orders/' + orderNo + '/cancel', { reason })
}

/** 契约标注为「新增」（仪表盘销售统计） */
export function getOrderStatsSummary(
  params: { storeId?: number; days?: number },
  silent = false,
): Promise<OrderStatsVO> {
  return http.get<OrderStatsVO>('/api/admin/order/stats/summary', params, { silent })
}
