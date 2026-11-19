package com.teaclub.common.context;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.system.entity.SysPermission;
import com.teaclub.system.entity.SysRole;
import com.teaclub.system.entity.SysUser;
import com.teaclub.system.entity.SysUserStore;
import com.teaclub.system.mapper.SysPermissionMapper;
import com.teaclub.system.mapper.SysRoleMapper;
import com.teaclub.system.mapper.SysUserMapper;
import com.teaclub.system.mapper.SysUserStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据用户 ID 实时装载登录用户主体（角色、权限、门店范围）。
 *
 * <p>放在 common 包是因为认证过滤器需要它，但其依赖集中在 system 域的 mapper。
 */
@Component
@RequiredArgsConstructor
public class TokenPrincipalResolver {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserStoreMapper userStoreMapper;

    public LoginUser resolve(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !"ENABLED".equals(user.getStatus())) {
            return null;
        }
        if (user.getDeleted() != null && user.getDeleted() == 1) {
            return null;
        }

        List<SysRole> roles = roleMapper.selectByUserId(userId);
        Set<String> roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
        boolean allStore = roles.stream().anyMatch(r -> "ALL".equals(r.getDataScope()));

        List<SysPermission> perms = permissionMapper.selectByUserId(userId);
        Set<String> permCodes = perms.stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toSet());

        Set<Long> storeIds = new HashSet<>();
        if (!allStore) {
            List<SysUserStore> rels = userStoreMapper.selectList(
                    Wrappers.<SysUserStore>lambdaQuery().eq(SysUserStore::getUserId, userId));
            storeIds = rels.stream().map(SysUserStore::getStoreId).collect(Collectors.toSet());
        }

        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .brandId(user.getBrandId())
                .userType(user.getUserType())
                .roles(roleCodes)
                .permissions(permCodes)
                .allStoreAccess(allStore)
                .storeIds(storeIds)
                .build();
    }
}
