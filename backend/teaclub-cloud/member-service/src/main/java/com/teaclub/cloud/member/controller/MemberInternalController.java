package com.teaclub.cloud.member.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * member-service 内部接口（仅供其他微服务调用）。
 *
 * <p>访问控制：由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}，
 * 未携带或凭证错误一律拒绝，不对外暴露。
 */
@Tag(name = "内部接口", description = "服务间调用专用，需携带内部凭证")
@RestController
@RequestMapping("/api/member/internal")
@Slf4j
@RequiredArgsConstructor
public class MemberInternalController {

    private final JdbcTemplate jdbcTemplate;


    @Operation(summary = "更新会员消费档案（内部调用）")
    @PostMapping("/user/{userId}/consume-stats")
    public Result<Void> updateConsumeStats(@PathVariable("userId") Long userId,
                                            @RequestBody Map<String, Object> stats) {
        // 增量累加：order-service 传入本次消费额与订单数增量
        jdbcTemplate.update(
                "UPDATE mem_member SET "
                        + "total_consume_amount = COALESCE(total_consume_amount,0) + ?, "
                        + "total_order_count = COALESCE(total_order_count,0) + ?, "
                        + "last_consume_time = ? "
                        + "WHERE id = ? AND deleted = 0",
                toLong(stats.get("consumeAmount")), toLong(stats.get("orderCount")),
                toDateTime(stats.get("lastConsumeTime")), userId);
        log.info("更新会员消费档案: memberId={} stats={}", userId, stats);
        return Result.ok();
    }

    private static long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static java.time.LocalDateTime toDateTime(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(String.valueOf(v));
        } catch (Exception e) {
            return java.time.LocalDateTime.now();
        }
    }

    @Operation(summary = "查询会员等级信息（内部调用）")
    @GetMapping("/level/{levelId}")
    public Result<Map<String, Object>> levelInfo(@PathVariable("levelId") Long levelId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, level_code, level_name, discount_rate, point_rate, status "
                        + "FROM mem_member_level WHERE id = ?", levelId);
        log.info("内部调用查询会员等级: levelId={}", levelId);
        return Result.ok(row);
    }

    @Operation(summary = "查询会员基础信息（内部调用）")
    @GetMapping("/user/{userId}")
    public Result<Map<String, Object>> findUserInfo(@PathVariable("userId") Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, member_no, nickname, phone, gender, level_id, register_store_id, "
                        + "total_consume_amount, total_recharge_amount, total_order_count, points, status "
                        + "FROM mem_member WHERE id = ? AND deleted = 0", userId);
        if (rows.isEmpty()) {
            return Result.ok(null);
        }
        LoginUser current = UserContext.get();
        Map<String, Object> data = rows.get(0);
        data.put("service", "member-service");
        data.put("caller", current == null ? "anonymous" : current.getUsername());
        log.info("内部调用查询会员: userId={} caller={}", userId,
                current == null ? "anonymous" : current.getUsername());
        return Result.ok(data);
    }
}
