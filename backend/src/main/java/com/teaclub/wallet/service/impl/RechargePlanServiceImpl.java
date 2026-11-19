package com.teaclub.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.exception.BizException;
import com.teaclub.member.entity.MemMember;
import com.teaclub.member.entity.MemMemberLevel;
import com.teaclub.member.mapper.MemMemberLevelMapper;
import com.teaclub.member.mapper.MemMemberMapper;
import com.teaclub.wallet.dto.RechargePlanSaveRequest;
import com.teaclub.wallet.entity.WalRechargePlan;
import com.teaclub.wallet.entity.WalRechargePlanStore;
import com.teaclub.wallet.mapper.WalRechargePlanMapper;
import com.teaclub.wallet.mapper.WalRechargePlanStoreMapper;
import com.teaclub.wallet.service.RechargePlanService;
import com.teaclub.wallet.vo.RechargePlanVO;
import com.teaclub.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RechargePlanServiceImpl implements RechargePlanService {

    private final WalRechargePlanMapper planMapper;
    private final WalRechargePlanStoreMapper planStoreMapper;
    private final MemMemberMapper memberMapper;
    private final MemMemberLevelMapper levelMapper;

    @Override
    public List<RechargePlanVO> listAll(String status) {
        Long brandId = UserContext.currentBrandId();
        List<WalRechargePlan> plans = planMapper.selectList(Wrappers.<WalRechargePlan>lambdaQuery()
                .eq(WalRechargePlan::getBrandId, brandId)
                .eq(StringUtils.hasText(status), WalRechargePlan::getStatus, status)
                .orderByAsc(WalRechargePlan::getSort)
                .orderByAsc(WalRechargePlan::getId));
        return plans.stream().map(p -> toVO(p, true)).collect(Collectors.toList());
    }

    @Override
    public List<RechargePlanVO> listAvailable(Long storeId, Long memberId) {
        Long brandId = UserContext.currentBrandId();
        LocalDateTime now = LocalDateTime.now();

        MemMember member = memberId == null ? null : memberMapper.selectById(memberId);

        List<WalRechargePlan> plans = planMapper.selectList(Wrappers.<WalRechargePlan>lambdaQuery()
                .eq(WalRechargePlan::getBrandId, brandId)
                .eq(WalRechargePlan::getStatus, "ENABLED")
                .orderByAsc(WalRechargePlan::getSort));

        List<RechargePlanVO> result = new ArrayList<>();
        for (WalRechargePlan plan : plans) {
            if (!inTimeWindow(plan, now)) {
                continue;
            }
            if (!levelMatches(plan, member)) {
                continue;
            }
            List<Long> storeIds = loadStoreIds(plan.getId());
            if (storeId != null && !storeIds.isEmpty() && !storeIds.contains(storeId)) {
                continue;
            }
            result.add(toVO(plan, true));
        }
        return result;
    }

    private boolean inTimeWindow(WalRechargePlan plan, LocalDateTime now) {
        if (plan.getStartTime() != null && now.isBefore(plan.getStartTime())) {
            return false;
        }
        return plan.getEndTime() == null || !now.isAfter(plan.getEndTime());
    }

    private boolean levelMatches(WalRechargePlan plan, MemMember member) {
        if (plan.getMinLevelId() == null) {
            return true;
        }
        if (member == null || member.getLevelId() == null) {
            return false;
        }
        MemMemberLevel required = levelMapper.selectById(plan.getMinLevelId());
        MemMemberLevel current = levelMapper.selectById(member.getLevelId());
        if (required == null || current == null) {
            return false;
        }
        return current.getLevelNo() >= required.getLevelNo();
    }

    private List<Long> loadStoreIds(Long planId) {
        return planStoreMapper.selectList(Wrappers.<WalRechargePlanStore>lambdaQuery()
                        .eq(WalRechargePlanStore::getPlanId, planId))
                .stream().map(WalRechargePlanStore::getStoreId).collect(Collectors.toList());
    }

    private RechargePlanVO toVO(WalRechargePlan p, boolean withStores) {
        RechargePlanVO vo = new RechargePlanVO();
        vo.setId(p.getId());
        vo.setPlanCode(p.getPlanCode());
        vo.setPlanName(p.getPlanName());
        vo.setRechargeAmount(p.getRechargeAmount());
        vo.setGiftAmount(p.getGiftAmount());
        vo.setGiftExpireDays(p.getGiftExpireDays());
        vo.setGiftPoints(p.getGiftPoints());
        vo.setMinLevelId(p.getMinLevelId());
        if (p.getMinLevelId() != null) {
            MemMemberLevel lv = levelMapper.selectById(p.getMinLevelId());
            vo.setMinLevelName(lv == null ? null : lv.getLevelName());
        }
        vo.setCustomAmountEnabled(p.getCustomAmountEnabled());
        vo.setMinCustomAmount(p.getMinCustomAmount());
        vo.setMaxCustomAmount(p.getMaxCustomAmount());
        vo.setPerMemberLimit(p.getPerMemberLimit());
        vo.setSort(p.getSort());
        vo.setStatus(p.getStatus());
        vo.setStartTime(p.getStartTime());
        vo.setEndTime(p.getEndTime());
        vo.setRemark(p.getRemark());
        vo.setCreateTime(p.getCreateTime());
        if (withStores) {
            vo.setStoreIds(loadStoreIds(p.getId()));
        }
        vo.setEffective("ENABLED".equals(p.getStatus()) && inTimeWindow(p, LocalDateTime.now()));
        return vo;
    }

    @Override
    public RechargePlanVO detail(Long id) {
        return toVO(require(id), true);
    }

    private WalRechargePlan require(Long id) {
        WalRechargePlan p = planMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值方案不存在");
        }
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RechargePlanSaveRequest request) {
        Long brandId = UserContext.currentBrandId();
        validate(request);

        Long dup = planMapper.selectCount(Wrappers.<WalRechargePlan>lambdaQuery()
                .eq(WalRechargePlan::getBrandId, brandId)
                .eq(WalRechargePlan::getPlanCode, request.getPlanCode()));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "方案编码已存在");
        }

        WalRechargePlan plan = new WalRechargePlan();
        plan.setBrandId(brandId);
        copy(request, plan);
        planMapper.insert(plan);
        bindStores(plan.getId(), request.getStoreIds());
        return plan.getId();
    }

    private void validate(RechargePlanSaveRequest r) {
        if (r.getStartTime() != null && r.getEndTime() != null
                && r.getStartTime().isAfter(r.getEndTime())) {
            throw new BizException(ResultCode.PARAM_INVALID, "活动开始时间不能晚于结束时间");
        }
        if (r.getCustomAmountEnabled() != null && r.getCustomAmountEnabled() == 1) {
            if (r.getMinCustomAmount() == null || r.getMinCustomAmount() <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "自定义金额方案必须设置下限");
            }
            if (r.getMaxCustomAmount() != null && r.getMaxCustomAmount() > 0
                    && r.getMaxCustomAmount() < r.getMinCustomAmount()) {
                throw new BizException(ResultCode.PARAM_INVALID, "自定义金额上限不能小于下限");
            }
        } else {
            if (r.getRechargeAmount() == null || r.getRechargeAmount() <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "固定金额方案的充值金额必须大于 0");
            }
        }
    }

    private void copy(RechargePlanSaveRequest r, WalRechargePlan p) {
        p.setPlanCode(r.getPlanCode());
        p.setPlanName(r.getPlanName());
        p.setRechargeAmount(r.getRechargeAmount() == null ? 0L : r.getRechargeAmount());
        p.setGiftAmount(r.getGiftAmount() == null ? 0L : r.getGiftAmount());
        p.setGiftExpireDays(r.getGiftExpireDays() == null ? 365 : r.getGiftExpireDays());
        p.setGiftPoints(r.getGiftPoints() == null ? 0L : r.getGiftPoints());
        p.setMinLevelId(r.getMinLevelId());
        p.setCustomAmountEnabled(r.getCustomAmountEnabled() == null ? 0 : r.getCustomAmountEnabled());
        p.setMinCustomAmount(r.getMinCustomAmount() == null ? 0L : r.getMinCustomAmount());
        p.setMaxCustomAmount(r.getMaxCustomAmount() == null ? 0L : r.getMaxCustomAmount());
        p.setPerMemberLimit(r.getPerMemberLimit() == null ? 0 : r.getPerMemberLimit());
        p.setSort(r.getSort() == null ? 0 : r.getSort());
        p.setStatus(r.getStatus());
        p.setStartTime(r.getStartTime());
        p.setEndTime(r.getEndTime());
        p.setRemark(r.getRemark());
    }

    private void bindStores(Long planId, List<Long> storeIds) {
        planStoreMapper.delete(Wrappers.<WalRechargePlanStore>lambdaQuery()
                .eq(WalRechargePlanStore::getPlanId, planId));
        if (storeIds == null || storeIds.isEmpty()) {
            return;
        }
        for (Long sid : new LinkedHashSet<>(storeIds)) {
            WalRechargePlanStore rel = new WalRechargePlanStore();
            rel.setPlanId(planId);
            rel.setStoreId(sid);
            rel.setCreateTime(LocalDateTime.now());
            planStoreMapper.insert(rel);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RechargePlanSaveRequest request) {
        WalRechargePlan plan = require(id);
        validate(request);

        Long dup = planMapper.selectCount(Wrappers.<WalRechargePlan>lambdaQuery()
                .eq(WalRechargePlan::getBrandId, plan.getBrandId())
                .eq(WalRechargePlan::getPlanCode, request.getPlanCode())
                .ne(WalRechargePlan::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "方案编码已存在");
        }

        WalRechargePlan patch = new WalRechargePlan();
        patch.setId(id);
        copy(request, patch);
        planMapper.updateById(patch);
        bindStores(id, request.getStoreIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        planMapper.deleteById(id);
        planStoreMapper.delete(Wrappers.<WalRechargePlanStore>lambdaQuery()
                .eq(WalRechargePlanStore::getPlanId, id));
    }
}
