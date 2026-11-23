package com.teaclub.cloud.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 品牌配置内部接口（仅供其他微服务调用）。
 *
 * <p>访问控制：由 `InternalTokenFilter` 校验 `X-Internal-Token`。
 *
 * <p>品牌配置（扣款顺序等）属 auth-service 域，
 * 其他服务必须通过本接口获取，<b>不得直接访问 iam_db</b>。
 */
@Tag(name = "品牌内部接口", description = "服务间调用专用，需携带内部凭证")
@RestController
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
@Slf4j
public class BrandInternalController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "查询品牌扣款顺序")
    @GetMapping("/brand/{brandId}/deduct-order")
    public Result<String> getDeductOrder(@PathVariable("brandId") Long brandId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, brand_code, brand_name, wallet_deduct_order FROM sys_brand WHERE id = ?",
                brandId);
        if (row == null) {
            throw new BizException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        Object order = row.get("wallet_deduct_order");
        String value = order == null ? "GIFT_FIRST" : String.valueOf(order);
        log.info("查询品牌扣款顺序: brandId={} order={}", brandId, value);
        return Result.ok(value);
    }

    /**
     * 查询品牌退款规则（供 wallet-service 退卡时判断是否需审核等）。
     *
     * <p>返回字段：
     * <ul>
     *   <li>{@code deductOrder}：消费扣款顺序 GIFT_FIRST / PRINCIPAL_FIRST</li>
     *   <li>{@code needAudit}：退卡是否需要人工审核 1/0</li>
     *   <li>{@code dailyLimit}：每日退卡次数上限，0 表示不限</li>
     *   <li>{@code giftConsumedAction}：赠送金已被消费时 REJECT / ALLOW</li>
     * </ul>
     */
    @Operation(summary = "查询品牌退款规则")
    @GetMapping("/brand/{brandId}/refund-rule")
    public Result<Map<String, Object>> getRefundRule(@PathVariable("brandId") Long brandId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT wallet_deduct_order, refund_need_audit, refund_daily_limit, refund_gift_consumed_action "
                        + "FROM sys_brand WHERE id = ?", brandId);
        if (rows.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deductOrder", row.get("wallet_deduct_order") == null
                ? "GIFT_FIRST" : String.valueOf(row.get("wallet_deduct_order")));
        data.put("needAudit", row.get("refund_need_audit") == null
                ? 1 : ((Number) row.get("refund_need_audit")).intValue());
        data.put("dailyLimit", row.get("refund_daily_limit") == null
                ? 1 : ((Number) row.get("refund_daily_limit")).intValue());
        data.put("giftConsumedAction", row.get("refund_gift_consumed_action") == null
                ? "REJECT" : String.valueOf(row.get("refund_gift_consumed_action")));
        log.info("查询品牌退款规则: brandId={} data={}", brandId, data);
        return Result.ok(data);
    }
    @Operation(summary = "查询单个门店信息")
    @GetMapping("/store/{storeId}")
    public Result<Map<String, Object>> store(@PathVariable("storeId") Long storeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, brand_id, store_code, store_name, status FROM sys_store WHERE id = ?",
                storeId);
        if (rows.isEmpty()) {
            return Result.ok(null);
        }
        return Result.ok(rows.get(0));
    }

    @Operation(summary = "批量查询门店名称")
    @GetMapping("/stores/names")
    public Result<Map<Long, String>> storeNames(@RequestParam Long brandId,
                                                 @RequestParam List<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Result.ok(java.util.Collections.emptyMap());
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, store_name FROM sys_store WHERE brand_id = ? AND id IN ("
                        + storeIds.stream().map(s -> "?").collect(Collectors.joining(","))
                        + ")", 
                params(brandId, storeIds));
        Map<Long, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            Object name = row.get("store_name");
            if (id != null && name != null) {
                result.put(((Number) id).longValue(), String.valueOf(name));
            }
        }
        log.info("批量查询门店名称: brandId={} count={}", brandId, result.size());
        return Result.ok(result);
    }

    private Object[] params(Long brandId, List<Long> storeIds) {
        Object[] args = new Object[storeIds.size() + 1];
        args[0] = brandId;
        for (int i = 0; i < storeIds.size(); i++) {
            args[i + 1] = storeIds.get(i);
        }
        return args;
    }

    @Operation(summary = "查询品牌基础信息")
    @GetMapping("/brand/{brandId}")
    public Result<Map<String, Object>> getBrand(@PathVariable("brandId") Long brandId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_brand WHERE id = ?", brandId);
        if (row == null) {
            throw new BizException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        return Result.ok(row);
    }
}
