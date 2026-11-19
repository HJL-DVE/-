package com.teaclub.cloud.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.dto.MemberBriefDTO;
import com.teaclub.cloud.wallet.dto.MemberLevelBriefDTO;
import com.teaclub.cloud.wallet.dto.RechargePlanSaveRequest;
import com.teaclub.cloud.wallet.entity.WalRechargePlan;
import com.teaclub.cloud.wallet.entity.WalRechargePlanStore;
import com.teaclub.cloud.wallet.mapper.WalRechargePlanMapper;
import com.teaclub.cloud.wallet.mapper.WalRechargePlanStoreMapper;
import com.teaclub.cloud.wallet.service.RechargePlanService;
import com.teaclub.cloud.wallet.service.support.MemberLookup;
import com.teaclub.cloud.wallet.vo.RechargePlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 充值方案服务（由单体 backend 的 RechargePlanServiceImpl 迁移而来）。
 *
 * <p><b>与单体的差异</b>：会员等级与会员资料改为经 member-service 内部接口获取，
 * 本服务不访问 {@code mem_member} / {@code mem_member_level} 表。
 */
@Service
@RequiredArgsConstructor
public class RechargePlanServiceImpl implements RechargePlanService {

    private final WalRechargePlanMapper planMapper;
    private final WalRechargePlanStoreMapper planStoreMapper;
    private final MemberLookup memberLookup;

    @Override
    public List<RechargePlanVO> listAll(String status) {
        Long brandId = UserContext.currentBrandId();
        List<WalRechargePlan> plans = planMapper.selectList(Wrappers.<WalRechargePlan>lambdaQuery()
                .eq(WalRechargePlan::getBrandId, brandId)
                .eq(StringUtils.hasText(status), WalRechargePlan::getStatus, status)
                .orderByAsc(WalRechargePlan::getSort)
                .orderByAsc(WalRechargePlan::getId));
        return toVOs(plans, true);
    }

    @Override
    public List<RechargePlanVO> listAvailable(Long storeId, Long memberId) {
        Long brandId = UserContext.currentBrandId();
        LocalDateTime now = LocalDateTime.now();

        MemberBriefDTO member = memberId == null ? null : memberLookup.require(memberId);

        List<WalRechargePlan> plans = planMapper.selectList(Wrappers.<WalRechargePlan>lambdaQuery()
                .eq(WalRechargePlan::getBrandId, brandId)
                .eq(WalRechargePlan::getStatus, "ENABLED")
                .orderByAsc(WalRechargePlan::getSort));

        Map<Long, MemberLevelBriefDTO> levelMap = memberLookup.levelMap(
                plans.stream().map(WalRechargePlan::getMinLevelId).toList());

        List<RechargePlanVO> result = new ArrayList<>();
        for (WalRechargePlan plan : plans) {
            if (!inTimeWindow(plan, now)) {
                continue;
            }
            if (!levelMatches(plan, member, levelMap)) {
                continue;
            }
            List<Long> storeIds = loadStoreIds(plan.getId());
            if (storeId != null && !storeIds.isEmpty() && !storeIds.contains(storeId)) {
                continue;
            }
            result.add(toVO(plan, storeIds, levelMap));
        }
        return result;
    }

    private boolean inTimeWindow(WalRechargePlan plan, LocalDateTime now) {
        if (plan.getStartTime() != null && now.isBefore(plan.getStartTime())) {
            return false;
        }
        return plan.getEndTime() == null || !now.isAfter(plan.getEndTime());
    }

    private boolean levelMatches(WalRechargePlan plan, MemberBriefDTO member,
                                 Map<Long, MemberLevelBriefDTO> levelMap) {
        if (plan.getMinLevelId() == null) {
            return true;
        }
        if (member == null || member.getLevelNo() == null) {
            return false;
        }
        MemberLevelBriefDTO required = levelMap.get(plan.getMinLevelId());
        return required != null && required.getLevelNo() != null
                && member.getLevelNo() >= required.getLevelNo();
    }

    private List<Long> loadStoreIds(Long planId) {
        return planStoreMapper.selectList(Wrappers.<WalRechargePlanStore>lambdaQuery()
                        .eq(WalRechargePlanStore::getPlanId, planId))
                .stream().map(WalRechargePlanStore::getStoreId).collect(Collectors.toList());
    }

    /** 批量组装 VO：等级名与门店参与关系一次性加载，避免 N+1。 */
    private List<RechargePlanVO> toVOs(List<WalRechargePlan> plans, boolean withStores) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }
        Map<Long, MemberLevelBriefDTO> levelMap = memberLookup.levelMap(
                plans.stream().map(WalRechargePlan::getMinLevelId).toList());
        return plans.stream()
                .map(p -> toVO(p, withStores ? loadStoreIds(p.getId()) : null, levelMap))
                .collect(Collectors.toList());
    }

    private RechargePlanVO toVO(WalRechargePlan p, List<Long> storeIds,
                                Map<Long, MemberLevelBriefDTO> levelMap) {
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
            MemberLevelBriefDTO lv = levelMap.get(p.getMinLevelId());
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
        vo.setDescription(p.getRemark());
        vo.setCreateTime(p.getCreateTime());
        if (storeIds != null) {
            vo.setStoreIds(storeIds);
        }
        vo.setEffective("ENABLED".equals(p.getStatus()) && inTimeWindow(p, LocalDateTime.now()));
        return vo;
    }

    @Override
    public RechargePlanVO detail(Long id) {
        WalRechargePlan plan = require(id);
        Map<Long, MemberLevelBriefDTO> levelMap = memberLookup.levelMap(
                List.of(plan.getMinLevelId() == null ? -1L : plan.getMinLevelId()));
        return toVO(plan, loadStoreIds(plan.getId()), levelMap);
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
