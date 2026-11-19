package com.teaclub.cloud.wallet.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.wallet.dto.ConsumeOutCmd;
import com.teaclub.cloud.wallet.dto.RechargeInCmd;
import com.teaclub.cloud.wallet.dto.RefundInCmd;
import com.teaclub.cloud.wallet.dto.WalletChangeResult;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * wallet-service 内部接口（仅供其他微服务调用）。
 *
 * <p>访问控制：由 `InternalTokenFilter` 校验 `X-Internal-Token`，
 * 未携带或凭证错误一律拒绝，不对外暴露。
 *
 * <p><b>金额安全</b>：所有金额参数由调用方（服务端）计算后传入，
 * 绝不允许来自客户端请求体。
 */
@Tag(name = "钱包内部接口", description = "服务间调用专用，需携带内部凭证")
@RestController
@RequestMapping("/api/wallet/internal")
@RequiredArgsConstructor
@Slf4j
public class WalletInternalController {

    private final com.teaclub.cloud.wallet.service.WalletReconciliationService reconciliationService;


    private final WalletService walletService;

    @Operation(summary = "创建充值订单（内部调用）")
    @PostMapping("/recharge-order")
    public Result<String> createRechargeOrder(@RequestParam Long brandId,
                                               @RequestParam Long memberId,
                                               @RequestParam(required = false) Long storeId,
                                               @RequestParam Long rechargeAmount,
                                               @RequestParam(required = false) Long giftAmount,
                                               @RequestParam(required = false) Integer giftExpireDays) {
        return Result.ok(walletService.createRechargeOrder(
                brandId, memberId, storeId, rechargeAmount, giftAmount, giftExpireDays));
    }

    @Operation(summary = "充值支付成功入账（内部调用）")
    @PostMapping("/recharge-order/paid")
    public Result<WalletChangeResult> rechargePaid(@RequestBody Map<String, Object> body) {
        String rechargeNo = String.valueOf(body.get("rechargeNo"));
        String paymentNo = body.get("paymentNo") == null ? null : String.valueOf(body.get("paymentNo"));
        return Result.ok(walletService.processRechargePaid(rechargeNo, paymentNo));
    }

    @Operation(summary = "查询充值订单（内部调用）")
    @GetMapping("/recharge-order/{rechargeNo}")
    public Result<Map<String, Object>> rechargeOrder(@PathVariable("rechargeNo") String rechargeNo) {
        Object raw = walletService.getRechargeOrder(rechargeNo);
        if (raw == null) {
            return Result.ok(null);
        }
        com.teaclub.cloud.wallet.entity.WalRechargeOrder o =
                (com.teaclub.cloud.wallet.entity.WalRechargeOrder) raw;
        // 以数据库列名（snake_case）返回，供 payment-service 统一按列名读取
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("recharge_no", o.getRechargeNo());
        data.put("brand_id", o.getBrandId());
        data.put("member_id", o.getMemberId());
        data.put("store_id", o.getStoreId());
        data.put("recharge_amount", o.getRechargeAmount());
        data.put("gift_amount", o.getGiftAmount());
        data.put("gift_expire_days", o.getGiftExpireDays());
        data.put("status", o.getStatus());
        data.put("payment_no", o.getPaymentNo());
        return Result.ok(data);
    }

    @Operation(summary = "资金对账（内部调用）")
    @GetMapping("/reconcile")
    public Result<Object> reconcile() {
        return Result.ok(reconciliationService.reconcile());
    }

    @Operation(summary = "冻结余额（内部调用，不修改账户余额）")
    @PostMapping("/freeze")
    public Result<Long> freeze(@RequestParam Long brandId,
                                @RequestParam Long memberId,
                                @RequestParam String orderNo,
                                @RequestParam Long freezePrincipal,
                                @RequestParam Long freezeGift,
                                @RequestParam(required = false) Integer expireMinutes) {
        return Result.ok(walletService.freeze(brandId, memberId, orderNo,
                freezePrincipal, freezeGift, expireMinutes));
    }

    @Operation(summary = "查询可用余额（内部调用）")
    @GetMapping("/available")
    public Result<Object> available(@RequestParam Long brandId, @RequestParam Long memberId) {
        return Result.ok(walletService.getAvailableBalance(brandId, memberId));
    }

    @Operation(summary = "确认冻结扣款（内部调用）")
    @PostMapping("/freeze/confirm")
    public Result<WalletChangeResult> confirmFreeze(@RequestParam Long brandId,
                                                     @RequestParam Long memberId,
                                                     @RequestParam String orderNo,
                                                     @RequestParam(required = false) Long storeId,
                                                     @RequestParam(required = false) Long operatorId) {
        return Result.ok(walletService.confirmFreeze(brandId, memberId, orderNo, storeId, operatorId));
    }

    @Operation(summary = "释放冻结（内部调用）")
    @PostMapping("/freeze/release")
    public Result<Boolean> releaseFreeze(@RequestParam Long brandId,
                                          @RequestParam Long memberId,
                                          @RequestParam String orderNo,
                                          @RequestParam(required = false) String reason) {
        return Result.ok(walletService.releaseFreeze(brandId, memberId, orderNo, reason));
    }

    @Operation(summary = "查询钱包（内部调用）")
    @GetMapping("/balance")
    public Result<WalWallet> getBalance(@RequestParam Long brandId, @RequestParam Long memberId) {
        return Result.ok(walletService.getWallet(brandId, memberId));
    }

    @Operation(summary = "充值入账（内部调用）")
    @PostMapping("/recharge-in")
    public Result<WalletChangeResult> rechargeIn(@RequestBody RechargeInCmd cmd) {
        log.info("内部调用充值入账: rechargeNo={} memberId={}", cmd.getRechargeNo(), cmd.getMemberId());
        return Result.ok(walletService.rechargeIn(cmd));
    }

    @Operation(summary = "消费扣款（内部调用）")
    @PostMapping("/consume-out")
    public Result<WalletChangeResult> consumeOut(@RequestBody ConsumeOutCmd cmd) {
        log.info("内部调用消费扣款: orderNo={} memberId={}", cmd.getOrderNo(), cmd.getMemberId());
        return Result.ok(walletService.consumeOut(cmd));
    }

    @Operation(summary = "退款回冲（内部调用）")
    @PostMapping("/refund-in")
    public Result<WalletChangeResult> refundIn(@RequestBody RefundInCmd cmd) {
        log.info("内部调用退款回冲: refundNo={} memberId={}", cmd.getRefundNo(), cmd.getMemberId());
        return Result.ok(walletService.refundIn(cmd));
    }

    @Operation(summary = "充值退款：扣回已入账金额（内部调用）")
    @PostMapping("/recharge-refund-out")
    public Result<WalletChangeResult> rechargeRefundOut(
            @RequestParam Long brandId,
            @RequestParam Long memberId,
            @RequestParam(required = false) Long storeId,
            @RequestParam String refundNo,
            @RequestParam Long principalAmount,
            @RequestParam Long giftAmount,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long operatorId) {
        log.info("内部调用充值退款: refundNo={} memberId={}", refundNo, memberId);
        return Result.ok(walletService.rechargeRefundOut(
                brandId, memberId, storeId, refundNo, principalAmount, giftAmount, remark, operatorId));
    }

    @Operation(summary = "人工调整余额（内部调用）")
    @PostMapping("/adjust")
    public Result<WalletChangeResult> adjust(
            @RequestParam Long brandId,
            @RequestParam Long memberId,
            @RequestParam(required = false) Long storeId,
            @RequestParam String approvalNo,
            @RequestParam Long principalChange,
            @RequestParam Long giftChange,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long operatorId) {
        log.info("内部调用人工调整: approvalNo={} memberId={}", approvalNo, memberId);
        return Result.ok(walletService.adjust(
                brandId, memberId, storeId, approvalNo, principalChange, giftChange, remark, operatorId));
    }
}
