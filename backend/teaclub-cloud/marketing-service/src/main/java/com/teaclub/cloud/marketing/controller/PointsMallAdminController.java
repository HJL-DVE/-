package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.entity.MktPointsGoods;
import com.teaclub.cloud.marketing.service.PointsMallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 积分商城管理端。 */
@Tag(name = "积分商城管理", description = "商品维护")
@RestController
@RequestMapping("/api/admin/marketing/points-goods")
@RequiredArgsConstructor
public class PointsMallAdminController {

    private final PointsMallService mallService;

    @Operation(summary = "商品列表")
    @GetMapping
    @PreAuthorize("hasAuthority('marketing:points:list')")
    public Result<List<MktPointsGoods>> list(@RequestParam(required = false) String status) {
        return Result.ok(mallService.listAll(status));
    }

    @Operation(summary = "新建商品")
    @PostMapping
    @PreAuthorize("hasAuthority('marketing:points:rule:update')")
    public Result<Long> create(@RequestBody MktPointsGoods goods) {
        return Result.ok(mallService.createGoods(goods));
    }

    @Operation(summary = "编辑商品")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:points:rule:update')")
    public Result<Void> update(@PathVariable Long id, @RequestBody MktPointsGoods goods) {
        mallService.updateGoods(id, goods);
        return Result.ok();
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:points:rule:update')")
    public Result<Void> delete(@PathVariable Long id) {
        mallService.deleteGoods(id);
        return Result.ok();
    }
}
