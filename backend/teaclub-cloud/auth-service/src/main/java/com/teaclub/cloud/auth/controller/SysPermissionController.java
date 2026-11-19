package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.web.annotation.AuditLog;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.auth.dto.PermissionSaveRequest;
import com.teaclub.cloud.auth.service.SysPermissionService;
import com.teaclub.cloud.auth.vo.PermissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "权限管理", description = "菜单权限与按钮权限维护")
@RestController
@RequestMapping("/api/admin/system/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService permissionService;

    @Operation(summary = "权限树（全量，用于角色授权）")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:permission:list')")
    public Result<List<PermissionVO>> tree() {
        return Result.ok(permissionService.tree());
    }

    @Operation(summary = "当前用户权限树（用于前端动态菜单）")
    @GetMapping("/mine")
    public Result<List<PermissionVO>> mine() {
        return Result.ok(permissionService.currentUserTree());
    }

    @Operation(summary = "权限详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:list')")
    public Result<PermissionVO> detail(@PathVariable Long id) {
        return Result.ok(permissionService.detail(id));
    }

    @Operation(summary = "新增权限")
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:grant')")
    @AuditLog(module = "权限管理", value = "新增权限点")
    public Result<Long> create(@Valid @RequestBody PermissionSaveRequest request) {
        return Result.ok(permissionService.create(request));
    }

    @Operation(summary = "编辑权限")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:grant')")
    @AuditLog(module = "权限管理", value = "编辑权限点")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PermissionSaveRequest request) {
        permissionService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:grant')")
    @AuditLog(module = "权限管理", value = "删除权限点")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.ok();
    }
}
