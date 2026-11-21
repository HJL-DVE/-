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

/**
 * 模拟微信支付成功（无真实商户，点击即支付）。
 *
 * 对应后端 POST /api/payment/mock/notify —— 网关白名单放行，不需要用户令牌；
 * 支付单状态与金额仍由后端校验（金额不符/单号不存在会被拒）。
 */
export function mockPayNotify(payload: { paymentNo: string; amount?: number }) {
  return http.post<string>('/api/payment/mock/notify', payload, { auth: false })
}