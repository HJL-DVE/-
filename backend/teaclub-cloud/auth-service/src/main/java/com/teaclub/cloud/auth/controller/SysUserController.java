package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.web.annotation.AuditLog;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.auth.dto.*;
import com.teaclub.cloud.auth.service.SysUserService;
import com.teaclub.cloud.auth.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "员工管理", description = "后台账号的增删改查、门店授权、状态管理")
@RestController
@RequestMapping("/api/admin/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "员工分页查询")
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<PageResult<UserVO>> page(UserQuery query) {
        return Result.ok(userService.page(query));
    }

    @Operation(summary = "员工详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @Operation(summary = "新增员工")
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    @AuditLog(module = "员工管理", value = "新增员工")
    public Result<Long> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @Operation(summary = "编辑员工")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @AuditLog(module = "员工管理", value = "编辑员工")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除员工")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @AuditLog(module = "员工管理", value = "删除员工")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset')")
    @AuditLog(module = "员工管理", value = "重置密码", saveParam = false)
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return Result.ok();
    }

    @Operation(summary = "启用/禁用员工")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:update')")
    @AuditLog(module = "员工管理", value = "变更员工状态")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateRequest request) {
        userService.updateStatus(id, request);
        return Result.ok();
    }

    @Operation(summary = "设置门店授权（数据权限）")
    @PutMapping("/{id}/stores")
    @PreAuthorize("hasAuthority('system:user:grant')")
    @AuditLog(module = "员工管理", value = "设置门店授权")
    public Result<Void> grantStores(@PathVariable Long id,
                                    @RequestBody GrantStoresRequest request) {
        userService.grantStores(id, request);
        return Result.ok();
    }

    @Operation(summary = "查询员工角色 ID 列表")
    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.ok(userService.listRoleIds(id));
    }

    @Operation(summary = "查询员工门店 ID 列表")
    @GetMapping("/{id}/stores")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<List<Long>> storeIds(@PathVariable Long id) {
        return Result.ok(userService.listStoreIds(id));
    }
}
