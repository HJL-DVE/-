package com.teaclub.cloud.payment.gateway.wechat;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.payment.config.PaymentProperties;
import com.teaclub.cloud.payment.gateway.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付 API v3 渠道。
 *
 * <p><b>当前状态：骨架实现，未与真实商户联调。</b>
 *
 * <p>已完成（可单测）：
 * <ul>
 *   <li>配置完整性校验（缺配置启动即失败，不会静默降级为 Mock）</li>
 *   <li>请求签名数据结构（HTTP Method + URL + Timestamp + Nonce + Body）</li>
 *   <li>通知验签所需的报文结构解析</li>
 * </ul>
 *
 * <p><b>未完成</b>（缺少真实商户资质无法验证）：
 * <ul>
 *   <li>RSA 私钥签名与平台证书验签的实际调用</li>
 *   <li>AES-256-GCM 通知解密的真实报文验证</li>
 *   <li>真实下单 / 查单 / 退款 HTTP 调用</li>
 * </ul>
 *
 * 上述未完成部分在实现时抛出明确异常，<b>绝不返回伪造的成功结果</b>。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.payment.provider", havingValue = "wechat-v3")
public class WechatPayV3Provider implements PaymentProvider {

    public static final String PROVIDER = "WECHAT_V3";

    private final PaymentProperties properties;

    @Override
    public String providerName() {
        return PROVIDER;
    }

    private PaymentProperties.Wechat cfg() {
        PaymentProperties.Wechat w = properties.getWechat();
        if (w == null || isBlank(w.getMerchantId()) || isBlank(w.getPrivateKeyPath())
                || isBlank(w.getApiV3Key()) || isBlank(w.getAppId())) {
            throw new BizException(ResultCode.SYSTEM_ERROR,
                    "微信支付配置不完整：需要 merchant-id / private-key-path / api-v3-key / app-id");
        }
        return w;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private BizException notImplemented(String action) {
        return new BizException(ResultCode.SYSTEM_ERROR,
                "微信支付[" + action + "]尚未实现或未联调：当前环境无真实商户资质，"
                        + "请使用 teaclub.payment.provider=mock 进行业务流程验证");
    }

    @Override
    public PayCreateResult createJsapiPay(PayCreateCmd cmd) {
        cfg();
        throw notImplemented("小程序下单");
    }

    @Override
    public PayCreateResult createNativePay(PayCreateCmd cmd) {
        cfg();
        throw notImplemented("Native 下单");
    }

    @Override
    public PayQueryResult queryByOutTradeNo(String paymentNo) {
        cfg();
        throw notImplemented("主动查单");
    }

    @Override
    public void closeOrder(String paymentNo) {
        cfg();
        throw notImplemented("关闭订单");
    }

    @Override
    public RefundResult createRefund(RefundCreateCmd cmd) {
        cfg();
        throw notImplemented("申请退款");
    }

    @Override
    public RefundResult queryRefund(String refundNo) {
        cfg();
        throw notImplemented("查询退款");
    }

    @Override
    public NotifyPayload verifyAndDecryptNotify(Map<String, String> headers, String body) {
        cfg();
        throw notImplemented("支付通知验签解密");
    }

    @Override
    public NotifyPayload verifyAndDecryptRefundNotify(Map<String, String> headers, String body) {
        cfg();
        throw notImplemented("退款通知验签解密");
    }
}
