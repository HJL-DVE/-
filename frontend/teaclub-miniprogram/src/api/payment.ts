import { http } from '@/utils/request'
import type { PaymentInfo } from './types'

/** POST /api/payment/create { bizType:"ORDER"|"RECHARGE", bizNo, openid? } */
export function createPayment(payload: { bizType: 'ORDER' | 'RECHARGE'; bizNo: string; openid?: string }) {
  return http.post<PaymentInfo>('/api/payment/create', payload)
}

/** GET /api/payment/orders/{paymentNo} —— 支付结果轮询 */
export function getPayment(paymentNo: string) {
  return http.get<PaymentInfo>('/api/payment/orders/' + paymentNo)
}
