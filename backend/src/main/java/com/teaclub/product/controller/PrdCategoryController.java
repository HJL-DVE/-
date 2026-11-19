package com.teaclub.product.controller;

import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.Result;
import com.teaclub.product.dto.CategorySaveRequest;
import com.teaclub.product.service.PrdCategoryService;
import com.teaclub.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "商品分类管理", description = "树形商品分类维护")
@RestController
@RequestMapping("/api/admin/product/categories")
@RequiredArgsConstructor
public class PrdCategoryController {

    private final PrdCategoryService categoryService;

    @Operation(summary = "分类树")
    @GetMapping
    @PreAuthorize("hasAuthority('product:category:list')")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    @Operation(summary = "分类详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:category:list')")
    public Result<CategoryVO> detail(@PathVariable Long id) {
        return Result.ok(categoryService.detail(id));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    @PreAuthorize("hasAuthority('product:category:create')")
    @AuditLog(module = "商品分类", value = "新增商品分类")
    public Result<Long> create(@Valid @RequestBody CategorySaveRequest request) {
        return Result.ok(categoryService.create(request));
    }

    @Operation(summary = "编辑分类")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:category:update')")
    @AuditLog(module = "商品分类", value = "编辑商品分类")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest request) {
        categoryService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:category:delete')")
    @AuditLog(module = "商品分类", value = "删除商品分类")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
