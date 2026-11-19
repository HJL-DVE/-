package com.teaclub.cloud.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.auth.dto.RoleSaveRequest;
import com.teaclub.cloud.auth.entity.SysRole;
import com.teaclub.cloud.auth.entity.SysRolePermission;
import com.teaclub.cloud.auth.entity.SysUserRole;
import com.teaclub.cloud.auth.mapper.SysRoleMapper;
import com.teaclub.cloud.auth.mapper.SysRolePermissionMapper;
import com.teaclub.cloud.auth.mapper.SysUserRoleMapper;
import com.teaclub.cloud.auth.service.SysRoleService;
import com.teaclub.cloud.auth.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public PageResult<RoleVO> page(Integer pageNo, Integer pageSize, String keyword, String status) {
        LoginUser current = UserContext.require();
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<SysRole> wrapper = Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getBrandId, current.getBrandId());
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysRole::getRoleName, kw).or().like(SysRole::getRoleCode, kw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId);

        Page<SysRole> page = roleMapper.selectPage(new Page<>(no, size), wrapper);
        List<RoleVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<RoleVO> listAll() {
        LoginUser current = UserContext.require();
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getBrandId, current.getBrandId())
                        .eq(SysRole::getStatus, "ENABLED")
                        .orderByAsc(SysRole::getSort))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setIsBuiltin(role.getIsBuiltin());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        List<Long> permIds = roleMapper.selectPermissionIds(role.getId());
        vo.setPermissionIds(permIds);
        vo.setPermissionCount(permIds.size());
        return vo;
    }

    @Override
    public RoleVO detail(Long id) {
        return toVO(requireRole(id));
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleSaveRequest request) {
        LoginUser current = UserContext.require();
        Long exists = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getBrandId, current.getBrandId())
                .eq(SysRole::getRoleCode, request.getRoleCode()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.ROLE_CODE_EXISTS);
        }

        SysRole role = new SysRole();
        role.setBrandId(current.getBrandId());
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDataScope(request.getDataScope());
        role.setIsBuiltin(0);
        role.setSort(request.getSort() == null ? 0 : request.getSort());
        role.setStatus("ENABLED");
        role.setRemark(request.getRemark());
        roleMapper.insert(role);

        if (request.getPermissionIds() != null) {
            grantPermissions(role.getId(), request.getPermissionIds());
        }
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleSaveRequest request) {
        SysRole role = requireRole(id);
        if (Objects.equals(role.getIsBuiltin(), 1)) {
            throw new BizException(ResultCode.BUILTIN_ROLE_IMMUTABLE);
        }
        Long dup = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getBrandId, role.getBrandId())
                .eq(SysRole::getRoleCode, request.getRoleCode())
                .ne(SysRole::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.ROLE_CODE_EXISTS);
        }

        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setRoleCode(request.getRoleCode());
        patch.setRoleName(request.getRoleName());
        patch.setDataScope(request.getDataScope());
        patch.setSort(request.getSort());
        patch.setRemark(request.getRemark());
        roleMapper.updateById(patch);

        if (request.getPermissionIds() != null) {
            grantPermissions(id, request.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (Objects.equals(role.getIsBuiltin(), 1)) {
            throw new BizException(ResultCode.BUILTIN_ROLE_IMMUTABLE);
        }
        Long used = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, id));
        if (used != null && used > 0) {
            throw new BizException(ResultCode.ROLE_IN_USE);
        }
        roleMapper.deleteById(id);
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantPermissions(Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        for (Long pid : new LinkedHashSet<>(permissionIds)) {
            SysRolePermission rel = new SysRolePermission();
            rel.setRoleId(roleId);
            rel.setPermissionId(pid);
            rel.setCreateTime(LocalDateTime.now());
            rolePermissionMapper.insert(rel);
        }
    }
}
