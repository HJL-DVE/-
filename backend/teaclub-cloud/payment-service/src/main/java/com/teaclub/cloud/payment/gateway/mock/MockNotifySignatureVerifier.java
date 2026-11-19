package com.teaclub.cloud.payment.gateway.mock;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Mock 支付通知校验器。
 *
 * <p><b>用途</b>：在本项目不接入真实微信商户的前提下，
 * 仍能真实地验证「通知身份校验」这段逻辑 ——
 * AppID 比对、商户号比对、签名校验三条规则全部实现并可被拒绝路径测试。
 *
 * <p><b>与真实微信的区别</b>（必须明确）：
 * <ul>
 *   <li>真实微信使用 RSA 平台证书验签 + AES-256-GCM 解密；
 *       本类使用 SHA-256(appId|mchId|paymentNo|amount|secret) 摘要，
 *       只是<b>同构的占位实现</b>，不具备真实密码学强度</li>
 *   <li>期望的 appId / mchId 来自配置，而非微信分配的商户资质</li>
 * </ul>
 *
 * <p><b>校验策略</b>：
 * <ul>
 *   <li>宽松模式（默认）：请求中<b>带了</b>哪个字段就校验哪个，未带则跳过</li>
 *   <li>严格模式（{@code enforce-notify-check=true}）：三个字段必须齐全，缺一即拒</li>
 * </ul>
 * 无论哪种模式，<b>字段存在但值不正确一律拒绝</b>，且支付单状态不变。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "teaclub.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockNotifySignatureVerifier {

    @Value("${teaclub.payment.mock.expected-app-id:mock-appid}")
    private String expectedAppId;

    @Value("${teaclub.payment.mock.expected-mch-id:mock-mchid}")
    private String expectedMchId;

    @Value("${teaclub.payment.mock.sign-secret:mock-sign-secret}")
    private String signSecret;

    @Value("${teaclub.payment.mock.enforce-notify-check:false}")
    private boolean enforce;

    /**
     * 校验通知身份信息。校验不通过直接抛异常，调用方不得继续处理。
     *
     * @param body 通知原始报文
     */
    public void verify(Map<String, Object> body) {
        String appId = str(body.get("appId"));
        String mchId = str(body.get("mchId"));
        String sign = str(body.get("sign"));

        if (enforce) {
            if (appId == null || mchId == null || sign == null) {
                log.error("[Mock通知] 严格模式要求 appId/mchId/sign 齐全");
                throw new BizException(ResultCode.BAD_REQUEST,
                        "通知缺少身份校验字段（appId/mchId/sign）");
            }
        }

        // 1. AppID 比对
        if (appId != null && !appId.equals(expectedAppId)) {
            log.error("[Mock通知] AppID 不匹配 期望={} 实际={}", expectedAppId, appId);
            throw new BizException(ResultCode.BAD_REQUEST,
                    "AppID 不匹配：期望 " + expectedAppId + "，收到 " + appId);
        }

        // 2. 商户号比对
        if (mchId != null && !mchId.equals(expectedMchId)) {
            log.error("[Mock通知] 商户号不匹配 期望={} 实际={}", expectedMchId, mchId);
            throw new BizException(ResultCode.BAD_REQUEST,
                    "商户号不匹配：期望 " + expectedMchId + "，收到 " + mchId);
        }

        // 3. 签名校验
        if (sign != null) {
            String expect = sign(appId == null ? expectedAppId : appId,
                    mchId == null ? expectedMchId : mchId,
                    str(body.get("paymentNo")),
                    str(body.get("amount")));
            if (!expect.equalsIgnoreCase(sign)) {
                log.error("[Mock通知] 签名校验失败 paymentNo={}", body.get("paymentNo"));
                throw new BizException(ResultCode.BAD_REQUEST, "通知验签失败");
            }
        }
    }

    /**
     * 生成 Mock 签名（供测试与文档使用）。
     *
     * <p>算法：SHA-256(appId|mchId|paymentNo|amount|secret) 取小写十六进制。
     */
    public String sign(String appId, String mchId, String paymentNo, String amount) {
        String raw = appId + "|" + mchId + "|" + paymentNo + "|" + amount + "|" + signSecret;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Mock 签名计算失败", e);
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
