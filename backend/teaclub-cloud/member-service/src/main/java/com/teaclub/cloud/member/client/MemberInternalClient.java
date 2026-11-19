package com.teaclub.cloud.member.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * member-service 对外提供的内部查询客户端。
 *
 * <p>供 order-service 等其它服务调用（会员身份与等级查询）。
 */
@FeignClient(name = "member-service", path = "/api/member/internal", contextId = "memberInternalClient")
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
     *
     * @param memberId 会员 ID
     * @param stats    统计增量
     */
    @PostMapping("/user/{memberId}/consume-stats")
    Result<Void> updateConsumeStats(@PathVariable("memberId") Long memberId,
                                    @RequestBody Map<String, Object> stats);
}
