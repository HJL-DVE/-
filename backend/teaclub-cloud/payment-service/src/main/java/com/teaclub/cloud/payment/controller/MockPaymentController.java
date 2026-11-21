package com.teaclub.cloud.payment.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.payment.service.MockPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mock 支付模拟接口。
 *
 * <p><b>仅在 teaclub.payment.provider=mock 时注册</b>；
 * provider=wechat-v3 时这些接口物理上不存在（路由 404），
 * 因此不可能在生产配置下被用来伪造支付成功。
 */
@Tag(name = "Mock 支付（仅开发环境）",
        description = "模拟支付/退款通知，用于无商户资质时验证完整支付链路")
@RestController
@RequestMapping("/api/payment/mock")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentController {

    private final MockPaymentService mockPaymentService;
    private final com.teaclub.cloud.payment.gateway.mock.MockNotifySignatureVerifier signatureVerifier;
    private final com.teaclub.cloud.payment.mapper.PayPaymentOrderMapper paymentMapper;

    @Operation(summary = "模拟支付成功通知")
    @PostMapping("/notify")
    public Result<String> notify(@RequestBody Map<String, Object> body) {
        // 身份校验：AppID / 商户号 / 签名。不通过则拒绝，支付单状态不变。
        signatureVerifier.verify(body);
        String paymentNo = (String) body.get("paymentNo");
        Long amount = body.get("amount") == null ? null
                : Long.valueOf(String.valueOf(body.get("amount")));
        return Result.ok(mockPaymentService.simulatePayNotify(paymentNo, amount));
    }

    @Operation(summary = "模拟支付失败")
    @PostMapping("/fail")
    public Result<String> fail(@RequestBody Map<String, Object> body) {
        String paymentNo = (String) body.get("paymentNo");
        String reason = (String) body.get("reason");
        return Result.ok(mockPaymentService.simulatePayFail(paymentNo, reason));
    }

    @Operation(summary = "模拟退款成功通知")
    @PostMapping("/refund-notify")
    public Result<String> refundNotify(@RequestBody Map<String, Object> body) {
        String refundNo = (String) body.get("refundNo");
        return Result.ok(mockPaymentService.simulateRefundNotify(refundNo));
    }

    /**
     * 支付单详情（免登录，供扫码端 H5 确认页读取）。
     *
     * <p><b>为什么免登录</b>：扫码的是顾客手机，没有后台账号也没有会员 token。
     * 只暴露「凭支付单号能查到的展示字段」——金额、状态、业务单号，
     * 不含会员身份、手机号等敏感信息；且仅在 provider=mock 时本接口才存在。
     *
     * <p>与会员端 {@code GET /api/payment/orders/{paymentNo}} 的区别：
     * 后者校验 payment.memberId 必须等于当前登录会员，本接口不做身份校验但限定字段。
     */
    @Operation(summary = "支付单详情（扫码确认页用，免登录）")
    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam String paymentNo) {
        com.teaclub.cloud.payment.entity.PayPaymentOrder payment = paymentMapper.selectByNo(paymentNo);
        if (payment == null) {
            throw new com.teaclub.cloud.core.exception.BizException(
                    com.teaclub.cloud.core.api.ResultCode.NOT_FOUND, "支付单不存在");
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("paymentNo", payment.getPaymentNo());
        data.put("bizNo", payment.getBizNo());
        data.put("amount", payment.getAmount());
        data.put("status", payment.getStatus());
        // 显式不返回：memberId / openid / 手机号 / 内部主键
        return Result.ok(data);
    }
}
