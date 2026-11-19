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

    private final WalletService walletService;

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
