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
}
