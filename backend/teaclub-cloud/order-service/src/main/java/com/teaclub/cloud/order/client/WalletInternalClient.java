package com.teaclub.cloud.order.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * order-service 调用 wallet-service。
 *
 * <p><b>关键约束</b>：钱包扣款是<b>有副作用</b>的操作，
 * 因此本客户端<b>不启用自动重试</b>。
 * 幂等由业务单号（订单号）保证 —— wallet-service 的
 * `uk_txn_biz(biz_type, biz_no)` 唯一索引会拒绝重复扣款。
 */
@FeignClient(name = "wallet-service", path = "/api/wallet/internal", contextId = "walletInternalClient")
public interface WalletInternalClient {

    /**
     * 消费扣款。
     *
     * @param cmd 含 orderNo（幂等键）、payableAmount、memberId、storeId
     */
    @PostMapping("/consume-out")
    Result<Map<String, Object>> consumeOut(@RequestBody Map<String, Object> cmd);

    /**
     * 退款回冲（按原资金构成）。
     */
    @PostMapping("/refund-in")
    Result<Map<String, Object>> refundIn(@RequestBody Map<String, Object> cmd);

    /**
     * 查询某业务单是否已有对应资金流水（跨服务对账用）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/txn-exists")
    com.teaclub.cloud.core.api.Result<Boolean> txnExists(
            @org.springframework.web.bind.annotation.RequestParam("bizType") String bizType,
            @org.springframework.web.bind.annotation.RequestParam("bizNo") String bizNo);
}
