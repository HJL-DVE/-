package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.web.annotation.AuditLog;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.auth.dto.BrandUpdateRequest;
import com.teaclub.cloud.auth.service.SysBrandService;
import com.teaclub.cloud.auth.vo.BrandVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "品牌管理", description = "品牌基础信息与储值规则配置")
@RestController
@RequestMapping("/api/admin/store/brand")
@RequiredArgsConstructor
public class SysBrandController {

    private final SysBrandService brandService;

    @Operation(summary = "获取当前品牌信息")
    @GetMapping
    @PreAuthorize("hasAuthority('brand:view')")
    public Result<BrandVO> current() {
        return Result.ok(brandService.current());
    }

    @Operation(summary = "更新品牌信息")
    @PutMapping
    @PreAuthorize("hasAuthority('brand:update')")
    @AuditLog(module = "品牌管理", value = "更新品牌信息")
    public Result<Void> update(@Valid @RequestBody BrandUpdateRequest request) {
        brandService.update(request);
        return Result.ok();
    }

    /**
     * 单独更新退款规则。
     *
     * <p><b>为什么独立接口</b>：退款规则是资金安全敏感配置，
     * 只允许「老板」级别（{@code brand:refund-rule:update}）修改，
     * 与品牌基础信息（logo、联系电话）的维护权限分开，
     * 避免运营人员顺手改动退款策略。
     */
    @Operation(summary = "更新退款规则（老板权限）")
    @PutMapping("/refund-rule")
    @PreAuthorize("hasAuthority('brand:refund-rule:update')")
    @AuditLog(module = "品牌管理", value = "更新退款规则")
    public Result<Void> updateRefundRule(@RequestBody BrandUpdateRequest request) {
        brandService.updateRefundRule(request);
        return Result.ok();
    }
}
