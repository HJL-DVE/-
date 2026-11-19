package com.teaclub.cloud.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.redis.cache.CacheService;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.auth.security.TokenPrincipalResolver;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.jwt.JwtProvider;
import com.teaclub.cloud.security.jwt.JwtProperties;
import com.teaclub.cloud.security.jwt.LoginProperties;
import com.teaclub.cloud.auth.dto.ChangePasswordRequest;
import com.teaclub.cloud.auth.dto.LoginRequest;
import com.teaclub.cloud.auth.entity.SysPermission;
import com.teaclub.cloud.auth.entity.SysStore;
import com.teaclub.cloud.auth.entity.SysUser;
import com.teaclub.cloud.auth.entity.SysUserStore;
import com.teaclub.cloud.auth.mapper.SysPermissionMapper;
import com.teaclub.cloud.auth.mapper.SysStoreMapper;
import com.teaclub.cloud.auth.mapper.SysUserMapper;
import com.teaclub.cloud.auth.mapper.SysUserStoreMapper;
import com.teaclub.cloud.auth.service.AuthService;
import com.teaclub.cloud.auth.vo.LoginVO;
import com.teaclub.cloud.auth.vo.MenuVO;
import com.teaclub.cloud.auth.vo.StoreBriefVO;
import com.teaclub.cloud.auth.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REVOKE_KEY_PREFIX = "auth:revoke:";

    private final SysUserMapper userMapper;
    private final SysStoreMapper storeMapper;
    private final SysUserStoreMapper userStoreMapper;
    private final SysPermissionMapper permissionMapper;
    private final TokenPrincipalResolver principalResolver;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginProperties loginProperties;
    private final CacheService cacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request, String clientIp) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));

        if (user == null) {
            throw new BizException(ResultCode.BAD_CREDENTIALS);
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new BizException(ResultCode.ACCOUNT_LOCKED,
                    "账号已锁定，请于 " + user.getLockUntil() + " 后重试");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFailure(user);
            throw new BizException(ResultCode.BAD_CREDENTIALS);
        }

        // 登录成功：重置失败计数
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLoginFailCount(0);
        update.setLockUntil(null);
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(clientIp);
        userMapper.updateById(update);

        LoginUser principal = principalResolver.resolve(user.getId());
        if (principal == null) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        String token = jwtProvider.issueAdminToken(principal);

        return LoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getExpireSeconds())
                .userInfo(buildUserInfo(principal, user))
                .build();
    }

    private void recordLoginFailure(SysUser user) {
        int fails = user.getLoginFailCount() == null ? 0 : user.getLoginFailCount();
        fails++;
        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setLoginFailCount(fails);
        if (fails >= loginProperties.getMaxFailCount()) {
            patch.setLockUntil(LocalDateTime.now().plusMinutes(loginProperties.getLockMinutes()));
            // 达到阈值后计数归零，避免无限叠加锁定
            patch.setLoginFailCount(0);
            log.warn("账号 {} 连续登录失败 {} 次，已锁定 {} 分钟",
                    user.getUsername(), fails, loginProperties.getLockMinutes());
        }
        userMapper.updateById(patch);
    }

    private UserInfoVO buildUserInfo(LoginUser principal, SysUser user) {
        List<StoreBriefVO> stores = loadAccessibleStores(principal);
        return UserInfoVO.builder()
                .userId(principal.getUserId())
                .username(principal.getUsername())
                .realName(principal.getRealName())
                .userType(principal.getUserType())
                .avatarUrl(user.getAvatarUrl())
                .brandId(principal.getBrandId())
                .roles(principal.getRoles())
                .permissions(principal.getPermissions())
                .dataScope(principal.isAllStoreAccess() ? "ALL" : "STORE")
                .stores(stores)
                .build();
    }

    /**
     * 加载当前用户可访问门店。ALL 范围返回品牌下全部门店，STORE 范围仅返回授权门店。
     */
    private List<StoreBriefVO> loadAccessibleStores(LoginUser principal) {
        List<SysStore> allStores = storeMapper.selectList(Wrappers.<SysStore>lambdaQuery()
                .eq(SysStore::getBrandId, principal.getBrandId())
                .eq(SysStore::getStatus, "OPEN")
                .orderByAsc(SysStore::getId));

        Map<Long, Boolean> defaultMap = new HashMap<>();
        if (!principal.isAllStoreAccess()) {
            List<SysUserStore> rels = userStoreMapper.selectList(Wrappers.<SysUserStore>lambdaQuery()
                    .eq(SysUserStore::getUserId, principal.getUserId()));
            for (SysUserStore r : rels) {
                defaultMap.put(r.getStoreId(), Objects.equals(r.getIsDefault(), 1));
            }
        }

        return allStores.stream()
                .filter(s -> principal.isAllStoreAccess() || defaultMap.containsKey(s.getId()))
                .map(s -> new StoreBriefVO(s.getId(), s.getStoreCode(), s.getStoreName(),
                        Boolean.TRUE.equals(defaultMap.get(s.getId()))))
                .collect(Collectors.toList());
    }

    @Override
    public UserInfoVO currentUserInfo() {
        LoginUser principal = UserContext.require();
        SysUser user = userMapper.selectById(principal.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return buildUserInfo(principal, user);
    }

    @Override
    public List<MenuVO> currentMenuTree() {
        LoginUser principal = UserContext.require();
        List<SysPermission> perms = permissionMapper.selectByUserId(principal.getUserId());

        List<MenuVO> menus = perms.stream()
                .filter(p -> "MENU".equals(p.getPermType()) && Objects.equals(p.getVisible(), 1))
                .sorted(Comparator.comparing(SysPermission::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysPermission::getId))
                .map(p -> {
                    MenuVO vo = new MenuVO();
                    vo.setId(p.getId());
                    vo.setParentId(p.getParentId());
                    vo.setPermCode(p.getPermCode());
                    vo.setPermName(p.getPermName());
                    vo.setPermType(p.getPermType());
                    vo.setRoutePath(p.getRoutePath());
                    vo.setComponent(p.getComponent());
                    vo.setIcon(p.getIcon());
                    vo.setSort(p.getSort());
                    return vo;
                })
                .collect(Collectors.toList());

        return buildTree(menus);
    }

    private List<MenuVO> buildTree(List<MenuVO> flat) {
        Map<Long, MenuVO> byId = new HashMap<>();
        for (MenuVO m : flat) {
            byId.put(m.getId(), m);
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO m : flat) {
            Long pid = m.getParentId();
            if (pid == null || pid == 0L || !byId.containsKey(pid)) {
                roots.add(m);
            } else {
                byId.get(pid).getChildren().add(m);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        LoginUser principal = UserContext.require();
        SysUser user = userMapper.selectById(principal.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_CREDENTIALS, "原密码错误");
        }
        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setPassword(passwordEncoder.encode(request.getNewPassword()));
        patch.setPasswordUpdateTime(LocalDateTime.now());
        userMapper.updateById(patch);
    }

    @Override
    public void logout(String token) {
        LoginUser principal = UserContext.get();
        if (principal != null && principal.getJti() != null) {
            cacheService.set(REVOKE_KEY_PREFIX + principal.getJti(), "1",
                    jwtProvider.getExpireSeconds(), TimeUnit.SECONDS);
        }
    }

    /**
     * 判断令牌是否已被吊销（登出后立即失效）。
     */
    public boolean isRevoked(String jti) {
        return jti != null && cacheService.get(REVOKE_KEY_PREFIX + jti) != null;
    }
}
