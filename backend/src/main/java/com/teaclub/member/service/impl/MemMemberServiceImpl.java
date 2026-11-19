package com.teaclub.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.context.LoginUser;
import com.teaclub.common.context.StoreScope;
import com.teaclub.common.context.UserContext;
import com.teaclub.common.exception.BizException;
import com.teaclub.member.dto.MemberQuery;
import com.teaclub.member.dto.MemberUpdateRequest;
import com.teaclub.member.entity.MemMember;
import com.teaclub.member.entity.MemMemberLevel;
import com.teaclub.member.mapper.MemMemberLevelMapper;
import com.teaclub.member.mapper.MemMemberMapper;
import com.teaclub.member.service.MemMemberService;
import com.teaclub.member.vo.MemberVO;
import com.teaclub.system.entity.SysStore;
import com.teaclub.system.mapper.SysStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemMemberServiceImpl implements MemMemberService {

    private final MemMemberMapper memberMapper;
    private final MemMemberLevelMapper levelMapper;
    private final SysStoreMapper storeMapper;

    @Override
    public PageResult<MemberVO> page(MemberQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        LambdaQueryWrapper<MemMember> wrapper = Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getBrandId, current.getBrandId());

        // ===== 数据权限（B 口径）=====
        // 门店角色可见：注册门店在授权范围内 **或** 在该门店消费过的会员。
        // 理由：会员属于品牌而非门店，会员跨店消费时收银台必须能识别身份；
        // 若只看注册门店，会出现"看得到订单却查不到会员"的矛盾。
        if (!scope.isAll()) {
            if (scope.isEmptyLimited()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            // 越权参数必须显式拒绝，不得静默忽略（安全要求）
            if (query.getRegisterStoreId() != null) {
                scope.assertAccessible(query.getRegisterStoreId());
            }
            List<Long> visibleIds = memberMapper.selectVisibleMemberIds(
                    current.getBrandId(), scope.getStoreIds());
            if (visibleIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(MemMember::getId, visibleIds);
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

        Map<Long, MemMemberLevel> levelMap = loadLevelMap();
        Map<Long, SysStore> storeMap = loadStoreMap();
        List<MemberVO> records = page.getRecords().stream()
                .map(m -> toVO(m, levelMap, storeMap, false))
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private Map<Long, MemMemberLevel> loadLevelMap() {
        return levelMapper.selectList(Wrappers.<MemMemberLevel>lambdaQuery())
                .stream().collect(Collectors.toMap(MemMemberLevel::getId, l -> l, (a, b) -> a));
    }

    private Map<Long, SysStore> loadStoreMap() {
        return storeMapper.selectList(Wrappers.<SysStore>lambdaQuery())
                .stream().collect(Collectors.toMap(SysStore::getId, s -> s, (a, b) -> a));
    }

    private MemberVO toVO(MemMember m, Map<Long, MemMemberLevel> levelMap,
                          Map<Long, SysStore> storeMap, boolean plainPhone) {
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
            SysStore s = storeMap.get(m.getRegisterStoreId());
            if (s != null) {
                vo.setRegisterStoreName(s.getStoreName());
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

    @Override
    public MemberVO findByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ResultCode.PARAM_INVALID, "手机号不能为空");
        }
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        MemMember m = memberMapper.selectOne(Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getBrandId, current.getBrandId())
                .eq(MemMember::getPhone, phone.trim()));
        if (m == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        // 数据权限（B 口径）：注册门店在授权范围 或 在本店消费过
        assertMemberVisible(m, scope);
        return toVO(m, loadLevelMap(), loadStoreMap(), true);
    }

    @Override
    public MemberVO detail(Long id) {
        MemMember m = requireMember(id);
        assertMemberScope(m);
        return toVO(m, loadLevelMap(), loadStoreMap(), true);
    }

    private void assertMemberScope(MemMember m) {
        assertMemberVisible(m, StoreScope.from(UserContext.require()));
    }

    /**
     * 会员可见性判定（B 口径）。
     *
     * <p>可见条件（满足任一）：
     * <ol>
     *   <li>会员注册门店在当前用户授权门店内</li>
     *   <li>会员在授权门店有过消费订单</li>
     * </ol>
     * 全量范围（总部）不受限。
     */
    private void assertMemberVisible(MemMember m, StoreScope scope) {
        if (scope.isAll()) {
            return;
        }
        if (scope.isEmptyLimited()) {
            throw new BizException(ResultCode.NO_STORE_PERMISSION, "当前账号未授权任何门店");
        }
        // 1) 注册门店在范围内
        if (m.getRegisterStoreId() != null && scope.canAccess(m.getRegisterStoreId())) {
            return;
        }
        // 2) 在范围内门店消费过
        List<Long> visible = memberMapper.selectVisibleMemberIds(m.getBrandId(), scope.getStoreIds());
        if (visible.contains(m.getId())) {
            return;
        }
        throw new BizException(ResultCode.NO_STORE_PERMISSION, "无权查看该会员");
    }

    private MemMember requireMember(Long id) {
        MemMember m = memberMapper.selectById(id);
        if (m == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MemberUpdateRequest request) {
        MemMember m = requireMember(id);
        assertMemberScope(m);

        if (request.getLevelId() != null) {
            MemMemberLevel level = levelMapper.selectById(request.getLevelId());
            if (level == null) {
                throw new BizException(ResultCode.LEVEL_NOT_FOUND);
            }
        }

        MemMember patch = new MemMember();
        patch.setId(id);
        patch.setNickname(request.getNickname());
        patch.setAvatarUrl(request.getAvatarUrl());
        patch.setGender(request.getGender());
        patch.setBirthday(request.getBirthday());
        patch.setLevelId(request.getLevelId());
        patch.setRemark(request.getRemark());
        memberMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status, String reason) {
        MemMember m = requireMember(id);
        assertMemberScope(m);
        if (!"NORMAL".equals(status) && !"FROZEN".equals(status)) {
            throw new BizException(ResultCode.PARAM_INVALID, "状态只能是 NORMAL 或 FROZEN");
        }
        MemMember patch = new MemMember();
        patch.setId(id);
        patch.setStatus(status);
        patch.setFreezeReason("FROZEN".equals(status) ? reason : null);
        memberMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MemMember m = requireMember(id);
        assertMemberScope(m);
        memberMapper.deleteById(id);
    }
}
