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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
