package com.teaclub.cloud.auth.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调用 marketing-service 触发营销活动。
 *
 * <p>目前只用于「会员注册成功后立刻发放新人礼包」。
 * 内部令牌由 FeignRequestInterceptor 统一注入。
 */
@FeignClient(name = "marketing-service", path = "/api/marketing/internal/activities",
        contextId = "authMarketingInternalClient")
public interface MarketingInternalClient {

    /** 触发新人礼包；返回实际发奖的活动条数。 */
    @PostMapping("/trigger-new-member")
    Result<Integer> triggerNewMember(@RequestParam("brandId") Long brandId,
                                     @RequestParam("memberId") Long memberId);
}
