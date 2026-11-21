package com.teaclub.cloud.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.member.client.AuthInternalClient;
import com.teaclub.cloud.member.dto.MemberCreateRequest;
import com.teaclub.cloud.member.dto.MemberQuery;
import com.teaclub.cloud.member.dto.MemberSelfUpdateRequest;
import com.teaclub.cloud.member.dto.MemberUpdateRequest;
import com.teaclub.cloud.member.entity.MemMember;
import com.teaclub.cloud.member.entity.MemMemberLevel;
import com.teaclub.cloud.member.mapper.MemMemberLevelMapper;
import com.teaclub.cloud.member.mapper.MemMemberMapper;
import com.teaclub.cloud.member.service.MemMemberService;
import com.teaclub.cloud.member.service.support.MemberNoAllocator;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.UserContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import com.teaclub.cloud.member.service.support.MemberVisibilityResolver;
import com.teaclub.cloud.member.vo.MemberStatsVO;
import com.teaclub.cloud.member.vo.MemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员服务实现（由单体 backend 的 MemMemberServiceImpl 迁移而来）。
 *
 * <p><b>关键改造</b>：会员可见口径（B 方案）的实现方式变了。
 * <ul>
 *   <li>单体：一个 SQL 的 `EXISTS` 子查询同时查 mem_member 与 oms_order（同库）</li>
 *   <li>微服务：order_db 归 order-service，改为 <b>Feign 调用</b>获取
 *       "在授权门店消费过的会员 ID"，再与本库按注册门店取并集</li>
 * </ul>
 *
 * <p>可见口径本身不变：注册门店在授权范围内 <b>或</b> 在该门店消费过。
 *
 * <p><b>钱包数据不迁移到本服务</b>。会员资料与资金账户分离管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemMemberServiceImpl implements MemMemberService {

    private final MemMemberMapper memberMapper;
    private final MemMemberLevelMapper levelMapper;
    private final MemberVisibilityResolver visibilityResolver;
    private final AuthInternalClient authClient;
    private final MemberNoAllocator memberNoAllocator;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<MemberVO> page(MemberQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        LambdaQueryWrapper<MemMember> wrapper = Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getBrandId, current.getBrandId());

        // ===== 数据权限（B 口径）=====
        if (!scope.isAll()) {
            if (scope.isEmptyLimited()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            // 越权参数必须显式拒绝，不得静默忽略（安全要求）
            if (query.getRegisterStoreId() != null) {
                scope.assertAccessible(query.getRegisterStoreId());
            }
            // 在授权门店消费过的会员（来自 order-service）
            Set<Long> consumedMemberIds = new HashSet<>(
                    visibilityResolver.resolveVisibleMemberIds(current.getBrandId(), scope));
            // 注册门店在授权范围内的会员（本地查询）
            List<Long> registeredIds = memberMapper.selectIdsByRegisterStores(
                    current.getBrandId(), scope.getStoreIds());
            consumedMemberIds.addAll(registeredIds);

            if (consumedMemberIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(MemMember::getId, consumedMemberIds);
        }

        // 注册门店精确筛选
        if (query.getRegisterStoreId() != null) {
            wrapper.eq(MemMember::getRegisterStoreId, query.getRegisterStoreId());
        }

        if (StringUtils.hasText(query.getPhone())) {
            wrapper.like(MemMember::getPhone, query.getPhone().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(MemMember::getNickname, kw).or().like(MemMember::getMemberNo, kw));
        }
        if (query.getLevelId() != null) {
            wrapper.eq(MemMember::getLevelId, query.getLevelId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MemMember::getStatus, query.getStatus());
        }
        if (query.getCreateTimeStart() != null) {
            wrapper.ge(MemMember::getCreateTime, query.getCreateTimeStart().atStartOfDay());
        }
        if (query.getCreateTimeEnd() != null) {
            wrapper.lt(MemMember::getCreateTime, query.getCreateTimeEnd().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(MemMember::getId);

        Page<MemMember> page = memberMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);

        Map<Long, MemMemberLevel> levelMap = loadLevels(page.getRecords());
        Map<Long, String> storeNameMap = loadStoreNames(current.getBrandId(), page.getRecords());

        List<MemberVO> voList = page.getRecords().stream()
                .map(m -> toVO(m, levelMap, storeNameMap, true))
                .collect(Collectors.toList());

        return new PageResult<>(voList, page.getTotal(), query.safePageNo(), query.safePageSize());
    }

    @Override
    public MemberVO findByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ResultCode.PARAM_INVALID, "手机号不能为空");
        }
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        MemMember m = memberMapper.selectOne(Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getBrandId, current.getBrandId())
                .eq(MemMember::getPhone, phone.trim())
                .last("LIMIT 1"));
        if (m == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND, "会员不存在");
        }
        // 可见性校验：注册门店在授权范围内 或 在该门店消费过
        if (!scope.isAll()) {
            boolean registered = m.getRegisterStoreId() != null
                    && scope.canAccess(m.getRegisterStoreId());
            boolean consumed = !visibilityResolver
                    .resolveVisibleMemberIds(current.getBrandId(), scope).isEmpty()
                    && visibilityResolver.resolveVisibleMemberIds(current.getBrandId(), scope)
                    .contains(m.getId());
            if (!registered && !consumed) {
                throw new BizException(ResultCode.NO_STORE_PERMISSION,
                        "没有该会员的数据权限");
            }
        }

        Map<Long, MemMemberLevel> levelMap = loadLevels(List.of(m));
        Map<Long, String> storeNameMap = loadStoreNames(current.getBrandId(), List.of(m));
        // 手机号脱敏（按手机号精确查询时也不返回明文）
        return toVO(m, levelMap, storeNameMap, false);
    }

    @Override
    public MemberVO findByMemberNo(String memberNo) {
        if (!StringUtils.hasText(memberNo)) {
            throw new BizException(ResultCode.PARAM_INVALID, "会员号不能为空");
        }
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        MemMember m = memberMapper.selectOne(Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getBrandId, current.getBrandId())
                .eq(MemMember::getMemberNo, memberNo.trim())
                .last("LIMIT 1"));
        if (m == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND, "会员不存在");
        }
        if (!scope.isAll()) {
            boolean registered = m.getRegisterStoreId() != null
                    && scope.canAccess(m.getRegisterStoreId());
            boolean consumed = visibilityResolver
                    .resolveVisibleMemberIds(current.getBrandId(), scope).contains(m.getId());
            if (!registered && !consumed) {
                throw new BizException(ResultCode.NO_STORE_PERMISSION, "没有该会员的数据权限");
            }
        }
        return toVO(m, loadLevels(List.of(m)),
                loadStoreNames(current.getBrandId(), List.of(m)), false);
    }

    @Override
    public MemberStatsVO stats() {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        Long brandId = current.getBrandId();

        // null 表示不限范围（总部账号）；非总部按可见会员集合过滤
        java.util.Collection<Long> scopeIds = null;
        if (!scope.isAll()) {
            List<Long> visible = visibilityResolver.resolveVisibleMemberIds(brandId, scope);
            if (visible.isEmpty()) {
                MemberStatsVO empty = new MemberStatsVO();
                empty.setTotalMembers(0L);
                empty.setNewMembers7d(0L);
                empty.setActiveMembers30d(0L);
                empty.setTotalPoints(0L);
                return empty;
            }
            scopeIds = visible;
        }

        MemberStatsVO vo = new MemberStatsVO();
        vo.setTotalMembers(memberMapper.countMembers(brandId, scopeIds));
        vo.setNewMembers7d(memberMapper.countMembersCreatedSince(brandId,
                java.time.LocalDateTime.now().minusDays(7), scopeIds));
        vo.setActiveMembers30d(memberMapper.countMembersActiveSince(brandId,
                java.time.LocalDateTime.now().minusDays(30), scopeIds));
        Long points = memberMapper.sumPoints(brandId, scopeIds);
        vo.setTotalPoints(points == null ? 0L : points);
        return vo;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long create(MemberCreateRequest request) {
        Long brandId = UserContext.require().getBrandId();
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        if (phone.isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "手机号不能为空");
        }
        // 手机号唯一（品牌内）
        Long exists = memberMapper.selectCount(Wrappers.<com.teaclub.cloud.member.entity.MemMember>lambdaQuery()
                .eq(com.teaclub.cloud.member.entity.MemMember::getBrandId, brandId)
                .eq(com.teaclub.cloud.member.entity.MemMember::getPhone, phone));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.MEMBER_PHONE_EXISTS);
        }
        // 门店数据权限：收银员/店长只能给本店建档，不能跨店
        StoreScope scope = StoreScope.from(UserContext.require());
        scope.assertAccessible(request.getRegisterStoreId());
        Long storeId = request.getRegisterStoreId() != null
                ? request.getRegisterStoreId()
                : (scope.isAll() ? null : scope.getStoreIds().iterator().next());
        if (storeId == null) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "请选择注册门店");
        }

        com.teaclub.cloud.member.entity.MemMember m = new com.teaclub.cloud.member.entity.MemMember();
        m.setBrandId(brandId);
        m.setPhone(phone);
        m.setPassword(passwordEncoder.encode(
                StringUtils.hasText(request.getPassword()) ? request.getPassword() : "TeaClub@123"));
        m.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname().trim() : "会员" + phone.substring(phone.length() - 4));
        m.setGender(request.getGender() == null ? "UNKNOWN" : request.getGender());
        m.setBirthday(request.getBirthday());
        m.setStatus("NORMAL");
        m.setLevelId(request.getLevelId() != null ? request.getLevelId() : defaultLevelId(brandId));
        m.setRegisterStoreId(storeId);
        m.setRegisterChannel("STAFF");   // 后台手工建档，区别于小程序 MINIAPP
        m.setPoints(0L);
        m.setTotalConsumeAmount(0L);
        m.setTotalRechargeAmount(0L);
        m.setTotalOrderCount(0);

        // 分配会员号，冲突重试（与小程序注册同一套）
        for (int attempt = 1; attempt <= 5; attempt++) {
            m.setId(null);
            m.setMemberNo(memberNoAllocator.next(brandId));
            try {
                memberMapper.insert(m);
                return m.getId();
            } catch (DataIntegrityViolationException | DeadlockLoserDataAccessException e) {
                // 会员号唯一键冲突 或 MySQL 死锁（分配器热点行竞争）都重试
                if (attempt == 5) {
                    throw new BizException(ResultCode.SYSTEM_ERROR, "会员建档冲突，请重试");
                }
                try { Thread.sleep(20L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        throw new BizException(ResultCode.SYSTEM_ERROR, "会员建档失败");
    }

    /** 品牌默认等级（最低折扣档），无配置时返回 1 */
    private Long defaultLevelId(Long brandId) {
        com.teaclub.cloud.member.entity.MemMemberLevel level = levelMapper.selectOne(
                Wrappers.<com.teaclub.cloud.member.entity.MemMemberLevel>lambdaQuery()
                        .eq(com.teaclub.cloud.member.entity.MemMemberLevel::getBrandId, brandId)
                        .eq(com.teaclub.cloud.member.entity.MemMemberLevel::getStatus, "ENABLED")
                        .orderByAsc(com.teaclub.cloud.member.entity.MemMemberLevel::getDiscountRate)
                        .last("LIMIT 1"));
        return level == null ? 1L : level.getId();
    }

    @Override
    public MemberVO detail(Long id) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        MemMember m = memberMapper.selectById(id);
        if (m == null || !current.getBrandId().equals(m.getBrandId())) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND, "会员不存在");
        }
        if (!scope.isAll()) {
            boolean registered = m.getRegisterStoreId() != null
                    && scope.canAccess(m.getRegisterStoreId());
            List<Long> visible = visibilityResolver.resolveVisibleMemberIds(current.getBrandId(), scope);
            if (!registered && !visible.contains(m.getId())) {
                throw new BizException(ResultCode.NO_STORE_PERMISSION, "没有该会员的数据权限");
            }
        }

        Map<Long, MemMemberLevel> levelMap = loadLevels(List.of(m));
        Map<Long, String> storeNameMap = loadStoreNames(current.getBrandId(), List.of(m));
        return toVO(m, levelMap, storeNameMap, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MemberUpdateRequest request) {
        LoginUser current = UserContext.require();
        MemberVO existing = detail(id);

        MemMember patch = new MemMember();
        patch.setId(id);
        if (request.getNickname() != null) {
            patch.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null) {
            patch.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getGender() != null) {
            patch.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            patch.setBirthday(request.getBirthday());
        }
        if (request.getRemark() != null) {
            patch.setRemark(request.getRemark());
        }
        memberMapper.updateById(patch);
        log.info("会员资料更新: id={} operator={}", id, current.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status, String freezeReason) {
        LoginUser current = UserContext.require();
        detail(id);

        if (status == null || status.isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "状态不能为空");
        }
        MemMember patch = new MemMember();
        patch.setId(id);
        patch.setStatus(status);
        patch.setFreezeReason(freezeReason);
        memberMapper.updateById(patch);
        log.info("会员状态变更: id={} status={} operator={}", id, status, current.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LoginUser current = UserContext.require();
        detail(id);
        memberMapper.deleteById(id);
        log.info("会员删除: id={} operator={}", id, current.getUsername());
    }

    @Override
    public MemberVO profile(Long memberId) {
        MemMember m = memberMapper.selectById(memberId);
        if (m == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND, "会员不存在");
        }
        return toVO(m, loadLevels(List.of(m)), loadStoreNames(m.getBrandId(), List.of(m)), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long memberId, MemberSelfUpdateRequest request) {
        MemMember existing = memberMapper.selectById(memberId);
        if (existing == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND, "会员不存在");
        }
        MemMember patch = new MemMember();
        patch.setId(memberId);
        if (request.getNickname() != null) {
            patch.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null) {
            patch.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getGender() != null) {
            patch.setGender(request.getGender());
        }
        memberMapper.updateById(patch);
        log.info("会员自助修改资料: memberId={}", memberId);
    }

    // ==================== 内部工具 ====================

    private Map<Long, MemMemberLevel> loadLevels(List<MemMember> members) {
        Set<Long> levelIds = members.stream()
                .map(MemMember::getLevelId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (levelIds.isEmpty()) {
            return Map.of();
        }
        List<MemMemberLevel> levels = levelMapper.selectBatchIds(levelIds);
        Map<Long, MemMemberLevel> map = new HashMap<>();
        for (MemMemberLevel l : levels) {
            map.put(l.getId(), l);
        }
        return map;
    }

    /**
     * 通过 auth-service 获取门店名称。
     */
    private Map<Long, String> loadStoreNames(Long brandId, List<MemMember> members) {
        Set<Long> storeIds = members.stream()
                .map(MemMember::getRegisterStoreId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        try {
            var resp = authClient.storeNames(brandId, new ArrayList<>(storeIds));
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.warn("获取门店名称失败，返回空: brandId={} stores={}", brandId, storeIds, e);
        }
        return Map.of();
    }

    private MemberVO toVO(MemMember m, Map<Long, MemMemberLevel> levelMap,
                          Map<Long, String> storeNameMap, boolean plainPhone) {
        MemberVO vo = new MemberVO();
        vo.setId(m.getId());
        vo.setMemberNo(m.getMemberNo());
        vo.setPhone(plainPhone ? m.getPhone() : maskPhone(m.getPhone()));
        vo.setNickname(m.getNickname());
        vo.setAvatarUrl(m.getAvatarUrl());
        vo.setGender(m.getGender());
        vo.setBirthday(m.getBirthday());
        vo.setLevelId(m.getLevelId());
        if (m.getLevelId() != null) {
            MemMemberLevel l = levelMap.get(m.getLevelId());
            if (l != null) {
                vo.setLevelName(l.getLevelName());
                vo.setLevelCode(l.getLevelCode());
            }
        }
        vo.setRegisterStoreId(m.getRegisterStoreId());
        if (m.getRegisterStoreId() != null) {
            String name = storeNameMap.get(m.getRegisterStoreId());
            if (name != null) {
                vo.setRegisterStoreName(name);
            }
        }
        vo.setRegisterChannel(m.getRegisterChannel());
        vo.setTotalConsumeAmount(m.getTotalConsumeAmount());
        vo.setTotalRechargeAmount(m.getTotalRechargeAmount());
        vo.setTotalOrderCount(m.getTotalOrderCount());
        vo.setPoints(m.getPoints());
        vo.setLastConsumeTime(m.getLastConsumeTime());
        vo.setStatus(m.getStatus());
        vo.setFreezeReason(m.getFreezeReason());
        vo.setRemark(m.getRemark());
        vo.setCreateTime(m.getCreateTime());
        vo.setUpdateTime(m.getUpdateTime());
        return vo;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
