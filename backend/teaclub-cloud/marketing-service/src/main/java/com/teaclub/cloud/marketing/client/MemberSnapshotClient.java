package com.teaclub.cloud.marketing.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 调用 member-service 回写积分快照。
 *
 * <p>只写快照，不读数据做业务判断 —— 账本以 marketing_db 为准。
 * 内部令牌由 {@code FeignRequestInterceptor} 统一注入。
 */
@FeignClient(name = "member-service", path = "/api/member/internal",
        contextId = "marketingMemberInternalClient")
public interface MemberSnapshotClient {

    /** 会员简要信息（含 levelId，用于积分倍率）。 */
    @GetMapping("/user/{memberId}/brief")
    Result<Map<String, Object>> memberBrief(@PathVariable("memberId") Long memberId);

    @PostMapping("/user/{userId}/points-snapshot")
    Result<Void> syncPoints(@PathVariable("userId") Long userId,
                            @RequestBody Map<String, Object> body);

    /** 当月生日会员 ID（生日活动定时发奖用）。 */
    @GetMapping("/birthday-members")
    Result<java.util.List<Long>> birthdayMembers(@RequestParam("brandId") Long brandId,
                                                  @RequestParam("month") Integer month);

    /** 沉睡会员 ID（沉睡召回应活动定时发奖用）。 */
    @GetMapping("/sleeping-members")
    Result<java.util.List<Long>> sleepingMembers(@RequestParam("brandId") Long brandId,
                                                  @RequestParam("sleepingDays") Integer sleepingDays);
}
