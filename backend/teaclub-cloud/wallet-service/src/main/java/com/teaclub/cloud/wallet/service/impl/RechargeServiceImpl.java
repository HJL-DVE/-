package com.teaclub.cloud.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.client.BrandInternalClient;
import com.teaclub.cloud.wallet.dto.MemberBriefDTO;
import com.teaclub.cloud.wallet.dto.MemberLevelBriefDTO;
import com.teaclub.cloud.wallet.dto.RechargeCreateRequest;
import com.teaclub.cloud.wallet.dto.RechargeOrderQuery;
import com.teaclub.cloud.wallet.entity.WalRechargeOrder;
import com.teaclub.cloud.wallet.entity.WalRechargePlan;
import com.teaclub.cloud.wallet.entity.WalRechargePlanStore;
import com.teaclub.cloud.wallet.mapper.WalRechargeOrderMapper;
import com.teaclub.cloud.wallet.mapper.WalRechargePlanMapper;
import com.teaclub.cloud.wallet.mapper.WalRechargePlanStoreMapper;
import com.teaclub.cloud.wallet.service.RechargeService;
import com.teaclub.cloud.wallet.service.support.MemberLookup;
import com.teaclub.cloud.wallet.vo.RechargeOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 充值订单服务（由单体 backend 的 RechargeServiceImpl 迁移而来）。
 *
 * <p>安全要点：
 * <ul>
 *   <li>充值金额与赠送金额<b>全部由服务端按方案计算</b>，请求体不接受金额字段</li>
 *   <li>校验方案有效性、时间窗口、参与门店、会员等级、每人次数限制</li>
 *   <li>入账只走支付成功回调，本服务不写钱包</li>
 * </ul>
 *
 * <p><b>与单体的差异</b>：会员资料与等级改为通过 member-service 内部接口获取，
 * 门店名称通过 auth-service 内部接口获取，<b>本服务不访问其它服务的库</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeServiceImpl implements RechargeService {

    private final WalRechargeOrderMapper rechargeMapper;
    private final WalRechargePlanMapper planMapper;
    private final WalRechargePlanStoreMapper planStoreMapper;
    private final MemberLookup memberLookup;
    private final BrandInternalClient authClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderVO createRecharge(RechargeCreateRequest request) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        scope.assertAccessible(request.getStoreId());

        MemberBriefDTO member = memberLookup.require(request.getMemberId());
        if (member == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        if (!"NORMAL".equals(member.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "会员已被冻结，无法充值");
        }

        WalRechargePlan plan = resolvePlan(request);
        validatePlanUsable(plan, request.getStoreId(), member);

        // ===== 金额全部服务端计算 =====
        long rechargeAmount;
        long giftAmount;
        if (Objects.equals(plan.getCustomAmountEnabled(), 1)) {
            if (request.getCustomAmount() == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "该方案需填写自定义充值金额");
            }
            rechargeAmount = request.getCustomAmount();
            validateCustomAmount(plan, rechargeAmount);
            giftAmount = 0L;
        } else {
            rechargeAmount = plan.getRechargeAmount();
            giftAmount = plan.getGiftAmount();
        }

        // 每人次数限制
        if (plan.getPerMemberLimit() != null && plan.getPerMemberLimit() > 0) {
            long paid = rechargeMapper.countPaidByPlan(member.getId(), plan.getId());
            if (paid >= plan.getPerMemberLimit()) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "该方案每人限充值 " + plan.getPerMemberLimit() + " 次，已达上限");
            }
        }

        String rechargeNo = BizNoGenerator.rechargeNo();
        WalRechargeOrder order = new WalRechargeOrder();
        order.setBrandId(current.getBrandId());
        order.setRechargeNo(rechargeNo);
        order.setMemberId(member.getId());
        order.setStoreId(request.getStoreId());
        order.setPlanId(plan.getId());
        order.setRechargeAmount(rechargeAmount);
        order.setGiftAmount(giftAmount);
        order.setGiftExpireDays(plan.getGiftExpireDays() == null ? 365 : plan.getGiftExpireDays());
        order.setPayChannel(StringUtils.hasText(request.getPayChannel()) ? request.getPayChannel() : "MOCK");
        order.setStatus("PENDING");
        order.setRemark(request.getRemark());
        rechargeMapper.insert(order);

        log.info("创建充值订单 rechargeNo={} memberId={} 实付={} 赠送={}",
                rechargeNo, member.getId(), rechargeAmount, giftAmount);
        return toVO(order, Map.of(member.getId(), member), Map.of(), Map.of());
    }

    private WalRechargePlan resolvePlan(RechargeCreateRequest request) {
        if (request.getPlanId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "充值方案不能为空");
        }
        WalRechargePlan plan = planMapper.selectById(request.getPlanId());
        if (plan == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值方案不存在");
        }
        return plan;
    }

    private void validatePlanUsable(WalRechargePlan plan, Long storeId, MemberBriefDTO member) {
        if (!"ENABLED".equals(plan.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该充值方案已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (plan.getStartTime() != null && now.isBefore(plan.getStartTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该充值活动尚未开始");
        }
        if (plan.getEndTime() != null && now.isAfter(plan.getEndTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该充值活动已结束");
        }
        List<WalRechargePlanStore> rels = planStoreMapper.selectList(
                Wrappers.<WalRechargePlanStore>lambdaQuery().eq(WalRechargePlanStore::getPlanId, plan.getId()));
        if (!rels.isEmpty()) {
            boolean joined = rels.stream().anyMatch(r -> Objects.equals(r.getStoreId(), storeId));
            if (!joined) {
                throw new BizException(ResultCode.BAD_REQUEST, "该门店未参与此充值活动");
            }
        }
        if (plan.getMinLevelId() != null) {
            Map<Long, MemberLevelBriefDTO> levelMap = memberLookup.levelMap(List.of(plan.getMinLevelId()));
            MemberLevelBriefDTO required = levelMap.get(plan.getMinLevelId());
            Integer currentLevelNo = member.getLevelNo();
            if (required == null || required.getLevelNo() == null || currentLevelNo == null
                    || currentLevelNo < required.getLevelNo()) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "该活动仅限 " + (required == null ? "指定" : required.getLevelName()) + " 及以上会员参与");
            }
        }
    }

    private void validateCustomAmount(WalRechargePlan plan, long amount) {
        long min = plan.getMinCustomAmount() == null ? 0 : plan.getMinCustomAmount();
        long max = plan.getMaxCustomAmount() == null ? 0 : plan.getMaxCustomAmount();
        if (min > 0 && amount < min) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值金额不能低于 " + min + " 分");
        }
        if (max > 0 && amount > max) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值金额不能高于 " + max + " 分");
        }
    }

    @Override
    public RechargeOrderVO detail(String rechargeNo) {
        WalRechargeOrder order = rechargeMapper.selectByNo(rechargeNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在");
        }
        StoreScope.from(UserContext.require()).assertAccessible(order.getStoreId());
        return toVOs(UserContext.currentBrandId(), List.of(order)).get(0);
    }

    @Override
    public PageResult<RechargeOrderVO> page(RechargeOrderQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        if (scope.isEmptyLimited()) {
            return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
        }

        LambdaQueryWrapper<WalRechargeOrder> wrapper = Wrappers.<WalRechargeOrder>lambdaQuery()
                .eq(WalRechargeOrder::getBrandId, current.getBrandId());
        if (!scope.isAll()) {
            wrapper.in(WalRechargeOrder::getStoreId, scope.getStoreIds());
        }
        if (query.getMemberId() != null) {
            wrapper.eq(WalRechargeOrder::getMemberId, query.getMemberId());
        }
        if (query.getStoreId() != null) {
            scope.assertAccessible(query.getStoreId());
            wrapper.eq(WalRechargeOrder::getStoreId, query.getStoreId());
        }
        if (query.getPlanId() != null) {
            wrapper.eq(WalRechargeOrder::getPlanId, query.getPlanId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(WalRechargeOrder::getStatus, query.getStatus());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(WalRechargeOrder::getCreateTime, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(WalRechargeOrder::getCreateTime, query.getEndDate().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(WalRechargeOrder::getId);

        Page<WalRechargeOrder> page = rechargeMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        return new PageResult<>(toVOs(current.getBrandId(), page.getRecords()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String rechargeNo, String reason) {
        WalRechargeOrder order = rechargeMapper.selectByNoForUpdate(rechargeNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在");
        }
        if ("PAID".equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "已支付的充值订单不能取消，请走退款流程");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return;
        }
        WalRechargeOrder patch = new WalRechargeOrder();
        patch.setId(order.getId());
        patch.setStatus("CANCELLED");
        patch.setCancelTime(LocalDateTime.now());
        patch.setRemark(reason);
        rechargeMapper.updateById(patch);
    }

    /** 批量补齐会员/门店/方案展示字段，避免逐行 N+1。 */
    private List<RechargeOrderVO> toVOs(Long brandId, List<WalRechargeOrder> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, MemberBriefDTO> memberMap = memberLookup.batchMap(
                list.stream().map(WalRechargeOrder::getMemberId).toList());
        Map<Long, String> storeNames = loadStoreNames(brandId,
                list.stream().map(WalRechargeOrder::getStoreId)
                        .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, WalRechargePlan> planMap = loadPlans(
                list.stream().map(WalRechargeOrder::getPlanId)
                        .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)));

        return list.stream().map(o -> toVO(o, memberMap, storeNames, planMap)).collect(Collectors.toList());
    }

    private RechargeOrderVO toVO(WalRechargeOrder o, Map<Long, MemberBriefDTO> memberMap,
                                 Map<Long, String> storeNames, Map<Long, WalRechargePlan> planMap) {
        RechargeOrderVO vo = new RechargeOrderVO();
        vo.setId(o.getId());
        vo.setRechargeNo(o.getRechargeNo());
        vo.setMemberId(o.getMemberId());
        MemberBriefDTO m = memberMap.get(o.getMemberId());
        if (m != null) {
            vo.setMemberName(m.getNickname() == null ? m.getMemberNo() : m.getNickname());
            vo.setMemberPhone(m.getPhone());
        }
        vo.setStoreId(o.getStoreId());
        if (o.getStoreId() != null) {
            vo.setStoreName(storeNames.get(o.getStoreId()));
        }
        vo.setPlanId(o.getPlanId());
        if (o.getPlanId() != null) {
            WalRechargePlan p = planMap.get(o.getPlanId());
            vo.setPlanName(p == null ? null : p.getPlanName());
        }
        vo.setRechargeAmount(o.getRechargeAmount());
        vo.setGiftAmount(o.getGiftAmount());
        // 实付金额 = 充值金额（赠送额不计入实付）
        vo.setActualAmount(o.getRechargeAmount());
        vo.setPayChannel(o.getPayChannel());
        vo.setStatus(o.getStatus());
        vo.setPaymentNo(o.getPaymentNo());
        vo.setPayTime(o.getPayTime());
        vo.setCreateTime(o.getCreateTime());
        vo.setRemark(o.getRemark());
        return vo;
    }

    private Map<Long, WalRechargePlan> loadPlans(java.util.Collection<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(WalRechargePlan::getId, x -> x, (a, b) -> a));
    }

    /** 门店名称来自 auth-service（展示型数据，下游异常降级为不展示）。 */
    private Map<Long, String> loadStoreNames(Long brandId, java.util.Collection<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            var resp = authClient.storeNames(brandId, List.copyOf(storeIds));
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.warn("获取门店名称失败，降级为不展示: brandId={} stores={}", brandId, storeIds, e);
        }
        return Collections.emptyMap();
    }
}
