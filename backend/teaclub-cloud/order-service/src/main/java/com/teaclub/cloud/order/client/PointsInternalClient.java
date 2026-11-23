package com.teaclub.cloud.order.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * order-service 调用 marketing-service 的积分内部接口。
 *
 * <p>营销库归 marketing-service 独占，order-service 不得直接访问 marketing_db。
 * 内部令牌由 FeignRequestInterceptor 统一注入。
 *
 * <p>简化方案：试算只查余额不扣，支付成功按订单号幂等扣一次，取消订单不扣。
 */
@FeignClient(name = "marketing-service", path = "/api/marketing/internal/points",
        contextId = "orderMarketingPointsClient")
public interface PointsInternalClient {

    /** 查询会员可用积分（仅查询，不扣减）。 */
    @GetMapping("/balance")
    Result<Long> balance(@RequestParam("memberId") Long memberId);

    /** 扣减积分（支付成功，按订单号幂等）。 */
    @PostMapping("/deduct")
    Result<Boolean> deduct(@RequestParam("memberId") Long memberId,
                           @RequestParam("points") long points,
                           @RequestParam("orderNo") String orderNo);
}
