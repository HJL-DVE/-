package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.web.annotation.AuditLog;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.auth.dto.RoleSaveRequest;
import com.teaclub.cloud.auth.service.SysRoleService;
import com.teaclub.cloud.auth.vo.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理", description = "角色增删改查与权限分配")
@RestController
@RequestMapping("/api/admin/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "角色分页查询")
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageResult<RoleVO>> page(@RequestParam(required = false) Integer pageNo,
                                           @RequestParam(required = false) Integer pageSize,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        return Result.ok(roleService.page(pageNo, pageSize, keyword, status));
    }

    @Operation(summary = "全部启用角色（下拉用）")
    @GetMapping("/all")
    public Result<List<RoleVO>> listAll() {
        return Result.ok(roleService.listAll());
    }

    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.detail(id));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    @AuditLog(module = "角色管理", value = "新增角色")
    public Result<Long> create(@Valid @RequestBody RoleSaveRequest request) {
        return Result.ok(roleService.create(request));
    }

    @Operation(summary = "编辑角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    @AuditLog(module = "角色管理", value = "编辑角色")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        roleService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @AuditLog(module = "角色管理", value = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "角色授权（分配权限）")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:grant')")
    @AuditLog(module = "角色管理", value = "角色授权")
    public Result<Void> grant(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        roleService.grantPermissions(id, permissionIds);
        return Result.ok();
    }
}
