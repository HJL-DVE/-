package com.teaclub.payment.service;

/**
 * Mock 支付模拟服务。
 *
 * <p><b>仅在 teaclub.payment.provider=mock 时可用</b>，用于在没有微信商户资质的情况下
 * 跑通完整支付链路。它走的是与真实通知<b>完全相同</b>的后处理路径，
 * 因此测的是真实业务逻辑，不是伪造结果。
 */
public interface MockPaymentService {

    /**
     * 模拟支付成功通知。
     *
     * @param paymentNo 支付单号
     * @param amount    模拟的通知金额（分）。传 null 则使用服务端记录金额。
     *                  用于测试"金额篡改被拒绝"的场景。
     * @return 处理结果说明
     */
    String simulatePayNotify(String paymentNo, Long amount);

    /** 模拟支付失败通知 */
    String simulatePayFail(String paymentNo, String reason);

    /** 模拟退款成功通知 */
    String simulateRefundNotify(String refundNo);
}
