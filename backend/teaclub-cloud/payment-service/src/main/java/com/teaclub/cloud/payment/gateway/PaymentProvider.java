package com.teaclub.cloud.payment.gateway;

/**
 * 支付渠道抽象。
 *
 * <p>业务代码<b>只依赖本接口</b>，不允许出现 {@code if (isMock)} 分支。
 * 具体实现由 {@code teaclub.payment.provider} 配置决定。
 *
 * <p>实现约定：
 * <ul>
 *   <li>{@code MockPaymentProvider}：无商户资质时使用，支付单 provider 必须标记为 MOCK，
 *       绝不伪装成真实微信支付成功</li>
 *   <li>{@code WechatPayV3Provider}：真实微信支付 API v3，落地验签与 AES-256-GCM 解密</li>
 * </ul>
 */
public interface PaymentProvider {

    /** 渠道标识：MOCK / WECHAT_V3 */
    String providerName();

    /** 创建预支付订单（小程序 JSAPI） */
    PayCreateResult createJsapiPay(PayCreateCmd cmd);

    /** 创建预支付订单（Web Native 扫码） */
    PayCreateResult createNativePay(PayCreateCmd cmd);

    /** 按平台支付单号主动查单 */
    PayQueryResult queryByOutTradeNo(String paymentNo);

    /** 关闭订单 */
    void closeOrder(String paymentNo);

    /** 申请退款 */
    RefundResult createRefund(RefundCreateCmd cmd);

    /** 查询退款 */
    RefundResult queryRefund(String refundNo);

    /**
     * 验签并解密支付通知。
     *
     * @param headers 验签相关请求头
     * @param body    原始请求体
     * @throws com.teaclub.common.exception.BizException 验签失败时抛出
     */
    NotifyPayload verifyAndDecryptNotify(java.util.Map<String, String> headers, String body);

    /** 验签并解密退款通知 */
    NotifyPayload verifyAndDecryptRefundNotify(java.util.Map<String, String> headers, String body);
}
