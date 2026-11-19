import { http } from '@/utils/request'
import type { OrderInfo, OrderQuote, PageResult } from './types'

export interface OrderItemPayload {
  skuId: number
  quantity: number
}

/** POST /api/order/quote —— 金额必须由服务端算 */
export function quoteOrder(payload: { storeId: number | string; items: OrderItemPayload[] }) {
  return http.post<OrderQuote>('/api/order/quote', payload)
}

/** POST /api/order/orders —— memberId 由令牌决定，请求体忽略 */
export function createOrder(payload: {
  storeId: number | string
  items: OrderItemPayload[]
  remark?: string
  orderSource?: string
}) {
  return http.post<OrderInfo>('/api/order/orders', payload)
}

/** GET /api/order/orders?status=&pageNo=&pageSize= */
export function getOrders(params: { status?: string; pageNo?: number; pageSize?: number }) {
  return http.get<PageResult<OrderInfo>>('/api/order/orders', params)
}

/** GET /api/order/orders/{orderNo} */
export function getOrder(orderNo: string) {
  return http.get<OrderInfo>('/api/order/orders/' + orderNo)
}

/** POST /api/order/orders/{orderNo}/cancel —— 仅 UNPAID */
export function cancelOrder(orderNo: string) {
  return http.post<boolean>('/api/order/orders/' + orderNo + '/cancel')
}

/** POST /api/order/orders/{orderNo}/pay-wallet */
export function payOrderWithWallet(orderNo: string) {
  return http.post<OrderInfo>('/api/order/orders/' + orderNo + '/pay-wallet')
}
