package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.entity.MktLevelBenefit;
import com.teaclub.cloud.marketing.service.LevelBenefitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 会员等级权益管理端。 */
@Tag(name = "等级权益管理", description = "等级营销权益配置（等级定义仍在 member-service）")
@RestController
@RequestMapping("/api/admin/marketing/level-benefits")
@RequiredArgsConstructor
public class LevelBenefitAdminController {

    private final LevelBenefitService benefitService;

    @Operation(summary = "全部等级的权益配置")
    @GetMapping
    @PreAuthorize("hasAuthority('marketing:level:list')")
    public Result<Map<Long, List<MktLevelBenefit>>> listAll() {
        return Result.ok(benefitService.listAll());
    }

    @Operation(summary = "某等级的权益")
    @GetMapping("/level/{levelId}")
    @PreAuthorize("hasAuthority('marketing:level:list')")
    public Result<List<MktLevelBenefit>> listByLevel(@PathVariable Long levelId) {
        return Result.ok(benefitService.listByLevel(levelId));
    }

    @Operation(summary = "新增或更新权益（同等级同编码唯一）")
    @PostMapping
    @PreAuthorize("hasAuthority('marketing:level:update')")
    public Result<Long> save(@RequestBody MktLevelBenefit benefit) {
        return Result.ok(benefitService.save(benefit));
    }

    @Operation(summary = "删除权益")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:level:update')")
    public Result<Void> delete(@PathVariable Long id) {
        benefitService.delete(id);
        return Result.ok();
    }
}
