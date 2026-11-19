package com.teaclub.cloud.payment.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
}
