package com.teaclub.cloud.order.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * order-service 调用 marketing-service 的优惠券内部接口。
 *
 * <p>营销库归 marketing-service 独占，order-service 不得直接访问 marketing_db。
 * 内部令牌由 FeignRequestInterceptor 统一注入。
 */
@FeignClient(name = "marketing-service", path = "/api/marketing/internal/coupons",
        contextId = "orderMarketingCouponClient")
public interface CouponInternalClient {

    /** 试算抵扣金额（不修改券状态）。按券实例 ID。 */
    @PostMapping("/quote-by-id")
    Result<Map<String, Object>> quoteById(@RequestParam("couponId") Long couponId,
                                          @RequestParam("memberId") Long memberId,
                                          @RequestParam(value = "storeId", required = false) Long storeId,
                                          @RequestParam("orderAmount") Long orderAmount);

    /** 锁券（按券实例 ID，下单占位 UNUSED→LOCKED）。 */
    @PostMapping("/lock-by-id")
    Result<Boolean> lockByCouponId(@RequestParam("couponId") Long couponId,
                                   @RequestParam("memberId") Long memberId,
                                   @RequestParam("orderNo") String orderNo);

    /** 锁券（按券号）。 */
    @PostMapping("/lock")
    Result<Boolean> lock(@RequestParam("couponNo") String couponNo,
                         @RequestParam("memberId") Long memberId,
                         @RequestParam("orderNo") String orderNo);

    /** 核销（按券实例 ID；order 侧只有 memberCouponId）。 */
    @PostMapping("/use-by-id")
    Result<Boolean> useByCouponId(@RequestParam("couponId") Long couponId,
                                  @RequestParam("orderNo") String orderNo,
                                  @RequestParam("discountAmount") Long discountAmount);

    /** 核销（按券号）。 */
    @PostMapping("/use")
    Result<Boolean> use(@RequestParam("couponNo") String couponNo,
                        @RequestParam("orderNo") String orderNo,
                        @RequestParam("discountAmount") Long discountAmount);

    /** 释放（订单取消 LOCKED→UNUSED）。 */
    @PostMapping("/release")
    Result<Boolean> release(@RequestParam("orderNo") String orderNo);
}