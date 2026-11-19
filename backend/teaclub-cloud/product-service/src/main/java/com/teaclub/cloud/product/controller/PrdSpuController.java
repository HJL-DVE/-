package com.teaclub.cloud.product.controller;

import com.teaclub.cloud.web.annotation.AuditLog;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.product.dto.SpuQuery;
import com.teaclub.cloud.product.dto.SpuSaveRequest;
import com.teaclub.cloud.product.service.PrdSpuService;
import com.teaclub.cloud.product.vo.SpuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "商品管理", description = "商品 SPU 与规格 SKU 维护")
@RestController
@RequestMapping("/api/admin/product/spus")
@RequiredArgsConstructor
public class PrdSpuController {

    private final PrdSpuService spuService;

    @Operation(summary = "商品分页查询")
    @GetMapping
    @PreAuthorize("hasAuthority('product:spu:list')")
    public Result<PageResult<SpuVO>> page(SpuQuery query) {
        return Result.ok(spuService.page(query));
    }

    @Operation(summary = "商品详情（含规格）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:spu:list')")
    public Result<SpuVO> detail(@PathVariable Long id) {
        return Result.ok(spuService.detail(id));
    }

    @Operation(summary = "新增商品")
    @PostMapping
    @PreAuthorize("hasAuthority('product:spu:create')")
    @AuditLog(module = "商品管理", value = "新增商品")
    public Result<Long> create(@Valid @RequestBody SpuSaveRequest request) {
        return Result.ok(spuService.create(request));
    }

    @Operation(summary = "编辑商品")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:spu:update')")
    @AuditLog(module = "商品管理", value = "编辑商品")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SpuSaveRequest request) {
        spuService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:spu:delete')")
    @AuditLog(module = "商品管理", value = "删除商品")
    public Result<Void> delete(@PathVariable Long id) {
        spuService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "上架/下架商品")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:spu:update')")
    @AuditLog(module = "商品管理", value = "变更商品状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        spuService.updateStatus(id, body.get("status"));
        return Result.ok();
    }
}
