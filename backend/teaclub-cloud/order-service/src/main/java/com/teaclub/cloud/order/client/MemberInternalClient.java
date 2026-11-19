package com.teaclub.cloud.order.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
     * 查询会员等级信息（含折扣率）。
     */
    @GetMapping("/level/{levelId}")
    Result<Map<String, Object>> levelInfo(@PathVariable("levelId") Long levelId);

    /**
     * 更新会员消费档案（累计消费额、订单数、最近消费时间）。
     */
    @PostMapping("/user/{memberId}/consume-stats")
    Result<Void> updateConsumeStats(@PathVariable("memberId") Long memberId,
                                    @RequestBody Map<String, Object> stats);
}
