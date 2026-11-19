package com.teaclub.payment.gateway.mock;

import com.teaclub.payment.gateway.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock 支付渠道。
 *
 * <p><b>重要约束</b>：
 * <ul>
 *   <li>产生的支付单 provider 恒为 {@code MOCK}，统计与报表可按此过滤，不污染真实营收</li>
 *   <li>不会因前端调用 wx.requestPayment 返回值而"自动成功"——支付成功必须由
 *       调用方显式触发 Mock 通知接口，走与真实支付完全相同的通知处理链路</li>
 *   <li>provider=wechat-v3 时本 Bean 不注册，Mock 通知接口物理上不存在</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "teaclub.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    public static final String PROVIDER = "MOCK";

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public PayCreateResult createJsapiPay(PayCreateCmd cmd) {
        log.info("[MOCK] 创建 JSAPI 预支付订单 paymentNo={} amount={}", cmd.getPaymentNo(), cmd.getAmount());
        String prepayId = "mock_prepay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("nonceStr", UUID.randomUUID().toString().replace("-", ""));
        params.put("package", "prepay_id=" + prepayId);
        params.put("signType", "RSA");
        params.put("paySign", "mock_sign_" + UUID.randomUUID().toString().replace("-", "").substring(0, 32));

        return PayCreateResult.builder()
                .success(true)
                .prepayId(prepayId)
                .invokeParams(params)
                .build();
    }

    @Override
    public PayCreateResult createNativePay(PayCreateCmd cmd) {
        log.info("[MOCK] 创建 Native 预支付订单 paymentNo={} amount={}", cmd.getPaymentNo(), cmd.getAmount());
        String codeUrl = "weixin://wxpay/bizpayurl?pr=MOCK"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return PayCreateResult.builder()
                .success(true)
                .codeUrl(codeUrl)
                .build();
    }

    @Override
    public PayQueryResult queryByOutTradeNo(String paymentNo) {
        // Mock 渠道不存在真实查单结果，返回 UNKNOWN 表示"需由运维确认"
        return PayQueryResult.builder()
                .tradeState("UNKNOWN")
                .failReason("Mock 渠道不支持主动查单，请通过 Mock 通知接口模拟支付结果")
                .build();
    }

    @Override
    public void closeOrder(String paymentNo) {
        log.info("[MOCK] 关闭订单 paymentNo={}", paymentNo);
    }

    @Override
    public RefundResult createRefund(RefundCreateCmd cmd) {
        log.info("[MOCK] 申请退款 refundNo={} amount={}", cmd.getRefundNo(), cmd.getRefundAmount());
        return RefundResult.builder()
                .success(true)
                .status("PROCESSING")
                .refundId("mock_refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .build();
    }

    @Override
    public RefundResult queryRefund(String refundNo) {
        return RefundResult.builder()
                .success(true)
                .status("PROCESSING")
                .failReason("Mock 渠道退款结果需通过 Mock 退款通知接口模拟")
                .build();
    }

    @Override
    public NotifyPayload verifyAndDecryptNotify(Map<String, String> headers, String body) {
        // Mock 渠道的"验签"仅做结构校验，并明确标记来源为 MOCK
        throw new UnsupportedOperationException(
                "Mock 渠道不解析微信报文；请调用 MockPaymentService.simulatePayNotify 显式模拟");
    }

    @Override
    public NotifyPayload verifyAndDecryptRefundNotify(Map<String, String> headers, String body) {
        throw new UnsupportedOperationException(
                "Mock 渠道不解析微信退款报文；请调用 MockPaymentService.simulateRefundNotify 显式模拟");
    }
}
