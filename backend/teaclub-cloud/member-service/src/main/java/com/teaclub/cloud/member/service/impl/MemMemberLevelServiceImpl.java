package com.teaclub.cloud.member.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.member.dto.MemberLevelSaveRequest;
import com.teaclub.cloud.member.entity.MemMember;
import com.teaclub.cloud.member.entity.MemMemberLevel;
import com.teaclub.cloud.member.mapper.MemMemberLevelMapper;
import com.teaclub.cloud.member.mapper.MemMemberMapper;
import com.teaclub.cloud.member.service.MemMemberLevelService;
import com.teaclub.cloud.member.vo.MemberLevelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemMemberLevelServiceImpl implements MemMemberLevelService {

    private final MemMemberLevelMapper levelMapper;
    private final MemMemberMapper memberMapper;

    @Override
    public List<MemberLevelVO> listAll() {
        LoginUser current = UserContext.require();
        return levelMapper.selectList(Wrappers.<MemMemberLevel>lambdaQuery()
                        .eq(MemMemberLevel::getBrandId, current.getBrandId())
                        .orderByAsc(MemMemberLevel::getLevelNo))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<MemberLevelVO> listForMember(Long memberId) {
        MemMember member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        return levelMapper.selectList(Wrappers.<MemMemberLevel>lambdaQuery()
                        .eq(MemMemberLevel::getBrandId, member.getBrandId())
                        .eq(MemMemberLevel::getStatus, "ENABLED")
                        .orderByAsc(MemMemberLevel::getLevelNo))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private MemberLevelVO toVO(MemMemberLevel l) {
        MemberLevelVO vo = new MemberLevelVO();
        vo.setId(l.getId());
        vo.setLevelCode(l.getLevelCode());
        vo.setLevelName(l.getLevelName());
        vo.setLevelNo(l.getLevelNo());
        vo.setUpgradeType(l.getUpgradeType());
        vo.setUpgradeThreshold(l.getUpgradeThreshold());
        vo.setDiscountRate(l.getDiscountRate());
        vo.setPointRate(l.getPointRate());
        vo.setIconUrl(l.getIconUrl());
        vo.setBenefits(l.getBenefits());
        vo.setStatus(l.getStatus());
        vo.setCreateTime(l.getCreateTime());
        Long count = memberMapper.selectCount(Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getLevelId, l.getId()));
        vo.setMemberCount(count == null ? 0L : count);
        return vo;
    }

    @Override
    public MemberLevelVO detail(Long id) {
        return toVO(require(id));
    }

    private MemMemberLevel require(Long id) {
        MemMemberLevel l = levelMapper.selectById(id);
        if (l == null) {
            throw new BizException(ResultCode.LEVEL_NOT_FOUND);
        }
        return l;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MemberLevelSaveRequest request) {
        LoginUser current = UserContext.require();
        Long dup = levelMapper.selectCount(Wrappers.<MemMemberLevel>lambdaQuery()
                .eq(MemMemberLevel::getBrandId, current.getBrandId())
                .eq(MemMemberLevel::getLevelCode, request.getLevelCode()));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.LEVEL_CODE_EXISTS);
        }

        MemMemberLevel level = new MemMemberLevel();
        level.setBrandId(current.getBrandId());
        copy(request, level);
        levelMapper.insert(level);
        return level.getId();
    }

    private void copy(MemberLevelSaveRequest r, MemMemberLevel l) {
        l.setLevelCode(r.getLevelCode());
        l.setLevelName(r.getLevelName());
        l.setLevelNo(r.getLevelNo());
        l.setUpgradeType(r.getUpgradeType());
        l.setUpgradeThreshold(r.getUpgradeThreshold());
        l.setDiscountRate(r.getDiscountRate());
        l.setPointRate(r.getPointRate());
        l.setIconUrl(r.getIconUrl());
        l.setBenefits(r.getBenefits());
        l.setStatus(r.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MemberLevelSaveRequest request) {
        MemMemberLevel level = require(id);
        Long dup = levelMapper.selectCount(Wrappers.<MemMemberLevel>lambdaQuery()
                .eq(MemMemberLevel::getBrandId, level.getBrandId())
                .eq(MemMemberLevel::getLevelCode, request.getLevelCode())
                .ne(MemMemberLevel::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.LEVEL_CODE_EXISTS);
        }
        MemMemberLevel patch = new MemMemberLevel();
        patch.setId(id);
        copy(request, patch);
        levelMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        Long used = memberMapper.selectCount(Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getLevelId, id));
        if (used != null && used > 0) {
            throw new BizException(ResultCode.LEVEL_IN_USE);
        }
        levelMapper.deleteById(id);
    }
}
