package com.teaclub.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.context.LoginUser;
import com.teaclub.common.context.StoreScope;
import com.teaclub.common.context.UserContext;
import com.teaclub.common.exception.BizException;
import com.teaclub.common.util.BizNoGenerator;
import com.teaclub.member.entity.MemMember;
import com.teaclub.member.entity.MemMemberLevel;
import com.teaclub.member.mapper.MemMemberLevelMapper;
import com.teaclub.member.mapper.MemMemberMapper;
import com.teaclub.system.entity.SysStore;
import com.teaclub.system.mapper.SysStoreMapper;
import com.teaclub.wallet.dto.RechargeCreateRequest;
import com.teaclub.wallet.dto.RechargeInCmd;
import com.teaclub.wallet.dto.RechargeOrderQuery;
import com.teaclub.wallet.entity.WalRechargeOrder;
import com.teaclub.wallet.entity.WalRechargePlan;
import com.teaclub.wallet.entity.WalRechargePlanStore;
import com.teaclub.wallet.mapper.WalRechargeOrderMapper;
import com.teaclub.wallet.mapper.WalRechargePlanMapper;
import com.teaclub.wallet.mapper.WalRechargePlanStoreMapper;
import com.teaclub.wallet.service.RechargeService;
import com.teaclub.wallet.service.WalletService;
import com.teaclub.wallet.vo.RechargeOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 充值业务服务。
 *
 * <p>安全要点：
 * <ul>
 *   <li>充值金额与赠送金额<b>全部由服务端按方案计算</b>，请求体不接受金额字段（自定义方案只接受金额数值，赠送额仍由规则决定）</li>
 *   <li>校验方案有效性、时间窗口、参与门店、会员等级、每人次数限制</li>
 *   <li>入账走 {@link WalletService#rechargeIn}，幂等键为充值单号</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeServiceImpl implements RechargeService {

    private final WalRechargeOrderMapper rechargeMapper;
    private final WalRechargePlanMapper planMapper;
    private final WalRechargePlanStoreMapper planStoreMapper;
    private final MemMemberMapper memberMapper;
    private final MemMemberLevelMapper levelMapper;
    private final SysStoreMapper storeMapper;
    private final WalletService walletService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderVO createRecharge(RechargeCreateRequest request) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        scope.assertAccessible(request.getStoreId());

        MemMember member = memberMapper.selectById(request.getMemberId());
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
            // 自定义金额按方案赠送比例计算，比例 = giftAmount / rechargeAmount（固定方案下）
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

        log.info("创建充值订单 rechargeNo={} memberId={} 金额={} 赠送={}",
                rechargeNo, member.getId(), rechargeAmount, giftAmount);
        return toVO(order);
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

    private void validatePlanUsable(WalRechargePlan plan, Long storeId, MemMember member) {
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
            MemMemberLevel required = levelMapper.selectById(plan.getMinLevelId());
            MemMemberLevel current = member.getLevelId() == null ? null
                    : levelMapper.selectById(member.getLevelId());
            if (required == null || current == null
                    || current.getLevelNo() < required.getLevelNo()) {
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
        return toVO(order);
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
        return new PageResult<>(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processPaySuccess(String rechargeNo, String paymentNo) {
        WalRechargeOrder order = rechargeMapper.selectByNoForUpdate(rechargeNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在: " + rechargeNo);
        }

        // 幂等：已支付直接返回
        if ("PAID".equals(order.getStatus())) {
            log.info("充值订单已支付，跳过重复处理 rechargeNo={}", rechargeNo);
            return;
        }
        if (!"PENDING".equals(order.getStatus()) && !"PAYING".equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "充值订单状态为 " + order.getStatus() + "，不可确认支付");
        }

        // 钱包入账（幂等键 = 充值单号）
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(order.getBrandId())
                .memberId(order.getMemberId())
                .storeId(order.getStoreId())
                .rechargeNo(order.getRechargeNo())
                .payAmount(order.getRechargeAmount())
                .giftAmount(order.getGiftAmount())
                .giftExpireDays(order.getGiftExpireDays())
                .operatorType("SYSTEM")
                .build());

        WalRechargeOrder patch = new WalRechargeOrder();
        patch.setId(order.getId());
        patch.setStatus("PAID");
        patch.setPaymentNo(paymentNo);
        patch.setPayTime(LocalDateTime.now());
        rechargeMapper.updateById(patch);

        // 累计充值统计
        MemMember member = memberMapper.selectById(order.getMemberId());
        if (member != null) {
            MemMember mp = new MemMember();
            mp.setId(member.getId());
            mp.setTotalRechargeAmount(
                    (member.getTotalRechargeAmount() == null ? 0 : member.getTotalRechargeAmount())
                            + order.getRechargeAmount());
            memberMapper.updateById(mp);
        }

        log.info("充值订单支付完成 rechargeNo={} paymentNo={}", rechargeNo, paymentNo);
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

    private RechargeOrderVO toVO(WalRechargeOrder o) {
        RechargeOrderVO vo = new RechargeOrderVO();
        vo.setId(o.getId());
        vo.setRechargeNo(o.getRechargeNo());
        vo.setMemberId(o.getMemberId());
        MemMember m = memberMapper.selectById(o.getMemberId());
        if (m != null) {
            vo.setMemberName(m.getNickname());
            vo.setMemberPhone(m.getPhone());
        }
        vo.setStoreId(o.getStoreId());
        SysStore s = storeMapper.selectById(o.getStoreId());
        if (s != null) {
            vo.setStoreName(s.getStoreName());
        }
        vo.setPlanId(o.getPlanId());
        if (o.getPlanId() != null) {
            WalRechargePlan p = planMapper.selectById(o.getPlanId());
            vo.setPlanName(p == null ? null : p.getPlanName());
        }
        vo.setRechargeAmount(o.getRechargeAmount());
        vo.setGiftAmount(o.getGiftAmount());
        vo.setPayChannel(o.getPayChannel());
        vo.setStatus(o.getStatus());
        vo.setPaymentNo(o.getPaymentNo());
        vo.setPayTime(o.getPayTime());
        vo.setCreateTime(o.getCreateTime());
        vo.setRemark(o.getRemark());
        return vo;
    }
}
