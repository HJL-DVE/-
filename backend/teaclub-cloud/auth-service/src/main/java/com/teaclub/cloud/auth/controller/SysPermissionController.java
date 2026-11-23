package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.auth.service.SysPermissionService;
import com.teaclub.cloud.auth.vo.PermissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限树查询。
 *
 * <p>权限点的维护（增删改）已从系统移除：权限编码被后端 {@code @PreAuthorize}
 * 和前端按钮权限引用，运行时修改会破坏鉴权。菜单与按钮权限由代码与数据库种子定义，
 * 本控制器仅提供角色授权所需的全量权限树查询。
 */
@Tag(name = "权限树", description = "角色授权用的权限树查询")
@RestController
@RequestMapping("/api/admin/system/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService permissionService;

    @Operation(summary = "权限树（全量，用于角色授权）")
    @GetMapping("/tree")
    // 用 system:role:grant 鉴权：能授权角色的人才需要读权限树。
    // 不再依赖 system:permission:list（该权限点随「权限管理」页面移除已禁用）。
    @PreAuthorize("hasAuthority('system:role:grant')")
    public Result<List<PermissionVO>> tree() {
        return Result.ok(permissionService.tree());
    }
}
