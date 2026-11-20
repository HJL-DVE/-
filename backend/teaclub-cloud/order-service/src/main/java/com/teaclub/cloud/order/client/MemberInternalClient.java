package com.teaclub.cloud.order.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * order-service 调用 member-service。
 *
 * <p>member_db 归 member-service 独占，order-service 不得直接访问。
 */
@FeignClient(name = "member-service", path = "/api/member/internal", contextId = "orderMemberInternalClient")
public interface MemberInternalClient {

    /**
     * 查询会员基础信息。
     */
    @GetMapping("/user/{userId}")
    Result<Map<String, Object>> userInfo(@PathVariable("userId") Long userId);

    /**
     * 会员简要信息（含 levelId）。
     */
    @GetMapping("/user/{memberId}/brief")
    Result<Map<String, Object>> memberBrief(@org.springframework.web.bind.annotation.PathVariable("memberId") Long memberId);

    /**
     * 查询会员等级信息（含折扣率）。
     */
    @GetMapping("/level/{levelId}")
    Result<Map<String, Object>> levelInfo(@PathVariable("levelId") Long levelId);

}
