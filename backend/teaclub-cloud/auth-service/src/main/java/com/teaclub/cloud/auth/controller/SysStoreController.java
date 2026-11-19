package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.web.annotation.AuditLog;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.auth.dto.StoreQuery;
import com.teaclub.cloud.auth.dto.StoreSaveRequest;
import com.teaclub.cloud.auth.service.SysStoreService;
import com.teaclub.cloud.auth.vo.StoreVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "门店管理", description = "多门店基础信息维护（受数据权限约束）")
@RestController
@RequestMapping("/api/admin/store/stores")
@RequiredArgsConstructor
public class SysStoreController {

    private final SysStoreService storeService;

    @Operation(summary = "门店分页查询（自动按数据权限过滤）")
    @GetMapping
    @PreAuthorize("hasAuthority('store:list')")
    public Result<PageResult<StoreVO>> page(StoreQuery query) {
        return Result.ok(storeService.page(query));
    }

    @Operation(summary = "当前用户可访问门店列表")
    @GetMapping("/accessible")
    public Result<List<StoreVO>> accessible() {
        return Result.ok(storeService.listAccessible());
    }

    @Operation(summary = "门店详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('store:list')")
    public Result<StoreVO> detail(@PathVariable Long id) {
        return Result.ok(storeService.detail(id));
    }

    @Operation(summary = "新增门店")
    @PostMapping
    @PreAuthorize("hasAuthority('store:create')")
    @AuditLog(module = "门店管理", value = "新增门店")
    public Result<Long> create(@Valid @RequestBody StoreSaveRequest request) {
        return Result.ok(storeService.create(request));
    }

    @Operation(summary = "编辑门店")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('store:update')")
    @AuditLog(module = "门店管理", value = "编辑门店")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody StoreSaveRequest request) {
        storeService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除门店")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('store:delete')")
    @AuditLog(module = "门店管理", value = "删除门店")
    public Result<Void> delete(@PathVariable Long id) {
        storeService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "变更门店状态")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('store:update')")
    @AuditLog(module = "门店管理", value = "变更门店状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || !status.matches("^(OPEN|SUSPENDED|CLOSED)$")) {
            return Result.fail(10002, "状态只能是 OPEN / SUSPENDED / CLOSED");
        }
        storeService.updateStatus(id, status);
        return Result.ok();
    }
}
