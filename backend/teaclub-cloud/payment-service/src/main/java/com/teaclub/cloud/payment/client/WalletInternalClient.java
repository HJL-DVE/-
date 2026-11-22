package com.teaclub.cloud.payment.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * payment-service 调用 wallet-service。
 *
 * <p><b>关键约束</b>：退款回冲是<b>有副作用</b>的操作，本客户端<b>不启用自动重试</b>。
 * 幂等由退款单号保证 —— wallet 侧 `uk_txn_biz(biz_type, biz_no)` 唯一索引兜底。
 */
@FeignClient(name = "wallet-service", path = "/api/wallet/internal", contextId = "paymentWalletInternalClient")
public interface WalletInternalClient {

    /** 查询充值订单（发起充值支付时校验金额与状态）。 */
    @GetMapping("/recharge-order/{rechargeNo}")
    Result<Map<String, Object>> rechargeOrder(@PathVariable("rechargeNo") String rechargeNo);

    /**
     * 充值支付成功入账（幂等键：充值单号）。
     *
     * <p>body: { rechargeNo, paymentNo }
     */
    @PostMapping("/recharge-order/paid")
    Result<Map<String, Object>> rechargePaid(@RequestBody Map<String, Object> body);

    /** 退款回冲（按原资金构成）。 */
    @PostMapping("/refund-in")
    Result<Map<String, Object>> refundIn(@RequestBody Map<String, Object> cmd);

    /**
     * 充值退卡：从钱包扣回原入账的实付金与赠送金（幂等键：refundNo）。
     *
     * <p>金额全部由 payment-service 根据充值单计算后传入，且 wallet 侧有余额校验，
     * 顾客已消费导致赠送余额不足时会直接拒绝，避免「钱退了、储值还在」。
     */
    @PostMapping("/recharge-refund-out")
    Result<Map<String, Object>> rechargeRefundOut(@RequestParam Long brandId,
                                                  @RequestParam Long memberId,
                                                  @RequestParam(required = false) Long storeId,
                                                  @RequestParam String refundNo,
                                                  @RequestParam Long principalAmount,
                                                  @RequestParam Long giftAmount,
                                                  @RequestParam(required = false) String remark,
                                                  @RequestParam(required = false) Long operatorId);

    /** 充值单标记退款完成（前置校验：必须已有扣回流水）。 */
    @PostMapping("/recharge-order/refunded")
    Result<Map<String, Object>> markRechargeRefunded(@RequestParam String rechargeNo,
                                                     @RequestParam String refundNo);

    /**
     * 人工调整余额。
     *
     * <p>仅用于退卡渠道受理失败时，把已从钱包扣回的金额原样加回；
     * 幂等键使用 {@code UNDO-<退款单号>}，并写入 ADMIN_ADJUST 流水便于对账。
     */
    @PostMapping("/adjust")
    Result<Map<String, Object>> adjust(@RequestParam Long brandId,
                                       @RequestParam Long memberId,
                                       @RequestParam(required = false) Long storeId,
                                       @RequestParam String approvalNo,
                                       @RequestParam Long principalChange,
                                       @RequestParam Long giftChange,
                                       @RequestParam(required = false) String remark,
                                       @RequestParam(required = false) Long operatorId);
}
