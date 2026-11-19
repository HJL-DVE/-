package com.teaclub.cloud.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.auth.dto.*;
import com.teaclub.cloud.auth.entity.*;
import com.teaclub.cloud.auth.mapper.*;
import com.teaclub.cloud.auth.service.SysUserService;
import com.teaclub.cloud.auth.vo.StoreBriefVO;
import com.teaclub.cloud.auth.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserStoreMapper userStoreMapper;
    private final SysStoreMapper storeMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysUser::getBrandId, current.getBrandId());

        // 数据权限：门店角色只能看到被授权门店下的账号
        if (!scope.isAll()) {
            if (scope.isEmptyLimited()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            List<Long> userIds = userStoreMapper.selectList(Wrappers.<SysUserStore>lambdaQuery()
                            .in(SysUserStore::getStoreId, scope.getStoreIds()))
                    .stream().map(SysUserStore::getUserId).distinct().collect(Collectors.toList());
            // 总部账号（HQ）对本店员工不可见，这里只按门店授权过滤
            if (userIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(SysUser::getId, userIds);
        }

        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getUsername, kw).or().like(SysUser::getRealName, kw));
        }
        if (StringUtils.hasText(query.getPhone())) {
            wrapper.like(SysUser::getPhone, query.getPhone().trim());
        }
        if (StringUtils.hasText(query.getUserType())) {
            wrapper.eq(SysUser::getUserType, query.getUserType());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        if (query.getStoreId() != null) {
            scope.assertAccessible(query.getStoreId());
            List<Long> ids = userStoreMapper.selectList(Wrappers.<SysUserStore>lambdaQuery()
                            .eq(SysUserStore::getStoreId, query.getStoreId()))
                    .stream().map(SysUserStore::getUserId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(SysUser::getId, ids);
        }

        wrapper.orderByDesc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);

        List<UserVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(maskPhoneForList(user.getPhone()));
        vo.setEmail(user.getEmail());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setUserType(user.getUserType());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setCreateTime(user.getCreateTime());

        List<SysRole> roles = roleMapper.selectByUserId(user.getId());
        vo.setRoleCodes(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
        vo.setRoleNames(roles.stream().map(SysRole::getRoleName).collect(Collectors.toList()));

        List<SysUserStore> rels = userStoreMapper.selectList(Wrappers.<SysUserStore>lambdaQuery()
                .eq(SysUserStore::getUserId, user.getId()));
        if (!rels.isEmpty()) {
            List<Long> storeIds = rels.stream().map(SysUserStore::getStoreId).collect(Collectors.toList());
            Map<Long, SysStore> storeMap = storeMapper.selectByIds(storeIds).stream()
                    .collect(Collectors.toMap(SysStore::getId, s -> s));
            List<StoreBriefVO> stores = rels.stream()
                    .map(r -> {
                        SysStore s = storeMap.get(r.getStoreId());
                        return s == null ? null : new StoreBriefVO(s.getId(), s.getStoreCode(),
                                s.getStoreName(), Objects.equals(r.getIsDefault(), 1));
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            vo.setStores(stores);
        } else {
            vo.setStores(Collections.emptyList());
        }
        return vo;
    }

    private String maskPhoneForList(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    @Override
    public UserVO detail(Long id) {
        SysUser user = requireUser(id);
        assertUserAccessible(user);
        UserVO vo = toVO(user);
        vo.setPhone(user.getPhone()); // 详情返回明文
        return vo;
    }

    /**
     * 校验当前用户是否有权操作目标账号。
     * 门店角色只能操作与其共享至少一个门店授权的账号。
     */
    private void assertUserAccessible(SysUser target) {
        LoginUser current = UserContext.require();
        if (current.getUserId().equals(target.getId())) {
            return;
        }
        StoreScope scope = StoreScope.from(current);
        if (scope.isAll()) {
            return;
        }
        List<Long> targetStores = userStoreMapper.selectList(Wrappers.<SysUserStore>lambdaQuery()
                        .eq(SysUserStore::getUserId, target.getId()))
                .stream().map(SysUserStore::getStoreId).collect(Collectors.toList());
        boolean share = targetStores.stream().anyMatch(scope.getStoreIds()::contains);
        if (!share) {
            throw new BizException(ResultCode.NO_STORE_PERMISSION, "无权访问该账号");
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateRequest request) {
        LoginUser current = UserContext.require();

        // 唯一索引包含已逻辑删除的数据，必须显式统计，避免出现 500
        long exists = userMapper.countByUsernameIncludingDeleted(current.getBrandId(), request.getUsername());
        if (exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS,
                    "登录账号 " + request.getUsername() + " 已被占用（可能属于已删除的账号）");
        }

        // 门店角色只能创建自己门店范围内的账号，且不能创建总部账号
        StoreScope scope = StoreScope.from(current);
        if (!scope.isAll()) {
            if ("HQ".equals(request.getUserType())) {
                throw new BizException(ResultCode.FORBIDDEN, "门店角色不能创建总部账号");
            }
            validateStoresInScope(scope, request.getStoreIds());
        }

        SysUser user = new SysUser();
        user.setBrandId(current.getBrandId());
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setUserType(request.getUserType());
        user.setStatus("ENABLED");
        user.setLoginFailCount(0);
        user.setPasswordUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        bindRoles(user.getId(), request.getRoleIds());
        bindStores(user.getId(), request.getStoreIds(), request.getDefaultStoreId());
        return user.getId();
    }

    private void validateStoresInScope(StoreScope scope, List<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "门店账号必须指定至少一个门店");
        }
        for (Long sid : storeIds) {
            scope.assertAccessible(sid);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpdateRequest request) {
        SysUser user = requireUser(id);
        assertUserAccessible(user);

        StoreScope scope = StoreScope.from(UserContext.require());
        if (!scope.isAll() && request.getStoreIds() != null) {
            validateStoresInScope(scope, request.getStoreIds());
        }

        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setRealName(request.getRealName());
        patch.setPhone(request.getPhone());
        patch.setEmail(request.getEmail());
        patch.setAvatarUrl(request.getAvatarUrl());
        userMapper.updateById(patch);

        if (request.getRoleIds() != null) {
            bindRoles(id, request.getRoleIds());
        }
        if (request.getStoreIds() != null) {
            bindStores(id, request.getStoreIds(), request.getDefaultStoreId());
        }
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BizException(ResultCode.NOT_FOUND, "角色不存在: " + roleId);
            }
            SysUserRole rel = new SysUserRole();
            rel.setUserId(userId);
            rel.setRoleId(roleId);
            rel.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(rel);
        }
    }

    private void bindStores(Long userId, List<Long> storeIds, Long defaultStoreId) {
        userStoreMapper.delete(Wrappers.<SysUserStore>lambdaQuery().eq(SysUserStore::getUserId, userId));
        if (storeIds == null || storeIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(storeIds);
        Long defaultId = defaultStoreId != null && distinct.contains(defaultStoreId)
                ? defaultStoreId : distinct.iterator().next();
        for (Long sid : distinct) {
            SysStore store = storeMapper.selectById(sid);
            if (store == null) {
                throw new BizException(ResultCode.STORE_NOT_FOUND, "门店不存在: " + sid);
            }
            SysUserStore rel = new SysUserStore();
            rel.setUserId(userId);
            rel.setStoreId(sid);
            rel.setIsDefault(Objects.equals(sid, defaultId) ? 1 : 0);
            rel.setCreateTime(LocalDateTime.now());
            userStoreMapper.insert(rel);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LoginUser current = UserContext.require();
        if (current.getUserId().equals(id)) {
            throw new BizException(ResultCode.CANNOT_DELETE_SELF);
        }
        SysUser user = requireUser(id);
        assertUserAccessible(user);

        userMapper.deleteById(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        userStoreMapper.delete(Wrappers.<SysUserStore>lambdaQuery().eq(SysUserStore::getUserId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPasswordRequest request) {
        SysUser user = requireUser(id);
        assertUserAccessible(user);
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPassword(passwordEncoder.encode(request.getNewPassword()));
        patch.setPasswordUpdateTime(LocalDateTime.now());
        patch.setLoginFailCount(0);
        patch.setLockUntil(null);
        userMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, StatusUpdateRequest request) {
        LoginUser current = UserContext.require();
        if (current.getUserId().equals(id) && !"ENABLED".equals(request.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能禁用当前登录账号");
        }
        SysUser user = requireUser(id);
        assertUserAccessible(user);

        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setStatus(request.getStatus());
        userMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantStores(Long id, GrantStoresRequest request) {
        SysUser user = requireUser(id);
        assertUserAccessible(user);

        StoreScope scope = StoreScope.from(UserContext.require());
        if (!scope.isAll() && request.getStoreIds() != null) {
            validateStoresInScope(scope, request.getStoreIds());
        }
        bindStores(id, request.getStoreIds(), request.getDefaultStoreId());
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        requireUser(userId);
        return userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    public List<Long> listStoreIds(Long userId) {
        requireUser(userId);
        return userStoreMapper.selectList(Wrappers.<SysUserStore>lambdaQuery()
                        .eq(SysUserStore::getUserId, userId))
                .stream().map(SysUserStore::getStoreId).collect(Collectors.toList());
    }
}