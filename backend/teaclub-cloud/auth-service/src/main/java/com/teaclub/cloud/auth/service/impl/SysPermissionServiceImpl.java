package com.teaclub.cloud.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.auth.dto.PermissionSaveRequest;
import com.teaclub.cloud.auth.entity.SysPermission;
import com.teaclub.cloud.auth.entity.SysRolePermission;
import com.teaclub.cloud.auth.mapper.SysPermissionMapper;
import com.teaclub.cloud.auth.mapper.SysRolePermissionMapper;
import com.teaclub.cloud.auth.service.SysPermissionService;
import com.teaclub.cloud.auth.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    @Override
    public List<PermissionVO> tree() {
        List<SysPermission> all = permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .orderByAsc(SysPermission::getSort)
                .orderByAsc(SysPermission::getId));
        return buildTree(all);
    }

    @Override
    public List<PermissionVO> currentUserTree() {
        LoginUser current = UserContext.require();
        List<SysPermission> perms = permissionMapper.selectByUserId(current.getUserId());
        perms.sort(Comparator.comparing(SysPermission::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysPermission::getId));
        return buildTree(perms);
    }

    private List<PermissionVO> buildTree(List<SysPermission> flat) {
        Map<Long, PermissionVO> map = new LinkedHashMap<>();
        for (SysPermission p : flat) {
            PermissionVO vo = new PermissionVO();
            vo.setId(p.getId());
            vo.setParentId(p.getParentId());
            vo.setPermCode(p.getPermCode());
            vo.setPermName(p.getPermName());
            vo.setPermType(p.getPermType());
            vo.setRoutePath(p.getRoutePath());
            vo.setComponent(p.getComponent());
            vo.setIcon(p.getIcon());
            vo.setSort(p.getSort());
            vo.setVisible(p.getVisible());
            vo.setStatus(p.getStatus());
            map.put(p.getId(), vo);
        }
        List<PermissionVO> roots = new ArrayList<>();
        for (PermissionVO vo : map.values()) {
            Long pid = vo.getParentId();
            if (pid == null || pid == 0L || !map.containsKey(pid)) {
                roots.add(vo);
            } else {
                map.get(pid).getChildren().add(vo);
            }
        }
        return roots;
    }

    @Override
    public PermissionVO detail(Long id) {
        SysPermission p = require(id);
        PermissionVO vo = new PermissionVO();
        vo.setId(p.getId());
        vo.setParentId(p.getParentId());
        vo.setPermCode(p.getPermCode());
        vo.setPermName(p.getPermName());
        vo.setPermType(p.getPermType());
        vo.setRoutePath(p.getRoutePath());
        vo.setComponent(p.getComponent());
        vo.setIcon(p.getIcon());
        vo.setSort(p.getSort());
        vo.setVisible(p.getVisible());
        vo.setStatus(p.getStatus());
        return vo;
    }

    private SysPermission require(Long id) {
        SysPermission p = permissionMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.PERMISSION_NOT_FOUND);
        }
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PermissionSaveRequest request) {
        Long dup = permissionMapper.selectCount(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermCode, request.getPermCode()));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "权限标识已存在");
        }
        if (request.getParentId() != null && request.getParentId() != 0L) {
            require(request.getParentId());
        }

        SysPermission p = new SysPermission();
        p.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        p.setPermCode(request.getPermCode());
        p.setPermName(request.getPermName());
        p.setPermType(request.getPermType());
        p.setRoutePath(request.getRoutePath());
        p.setComponent(request.getComponent());
        p.setIcon(request.getIcon());
        p.setSort(request.getSort());
        p.setVisible(request.getVisible());
        p.setStatus(request.getStatus());
        permissionMapper.insert(p);
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PermissionSaveRequest request) {
        require(id);
        Long dup = permissionMapper.selectCount(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermCode, request.getPermCode())
                .ne(SysPermission::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "权限标识已存在");
        }
        if (Objects.equals(request.getParentId(), id)) {
            throw new BizException(ResultCode.BAD_REQUEST, "父级不能是自己");
        }

        SysPermission patch = new SysPermission();
        patch.setId(id);
        patch.setParentId(request.getParentId());
        patch.setPermCode(request.getPermCode());
        patch.setPermName(request.getPermName());
        patch.setPermType(request.getPermType());
        patch.setRoutePath(request.getRoutePath());
        patch.setComponent(request.getComponent());
        patch.setIcon(request.getIcon());
        patch.setSort(request.getSort());
        patch.setVisible(request.getVisible());
        patch.setStatus(request.getStatus());
        permissionMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        Long children = permissionMapper.selectCount(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ResultCode.CATEGORY_HAS_CHILDREN, "该权限下存在子权限，无法删除");
        }
        permissionMapper.deleteById(id);
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getPermissionId, id));
    }
}
