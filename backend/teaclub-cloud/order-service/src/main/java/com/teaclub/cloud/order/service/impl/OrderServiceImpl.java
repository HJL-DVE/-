package com.teaclub.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.order.client.AuthInternalClient;
import com.teaclub.cloud.order.client.MemberInternalClient;
import com.teaclub.cloud.order.client.WalletInternalClient;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.order.dto.OrderCreateRequest;
import com.teaclub.cloud.order.dto.OrderQuery;
import com.teaclub.cloud.order.dto.OrderQuoteRequest;
import com.teaclub.cloud.order.entity.OmsOrder;
import com.teaclub.cloud.order.entity.OmsOrderItem;
import com.teaclub.cloud.order.entity.OmsOrderStatusLog;
import com.teaclub.cloud.order.enums.OrderStatus;
import com.teaclub.cloud.order.mapper.OmsOrderItemMapper;
import com.teaclub.cloud.order.mapper.OmsOrderMapper;
import com.teaclub.cloud.order.mapper.OmsOrderStatusLogMapper;
import com.teaclub.cloud.order.service.OrderAmountCalculator;
import com.teaclub.cloud.order.service.OrderService;
import com.teaclub.cloud.order.vo.OrderItemVO;
import com.teaclub.cloud.order.vo.OrderQuoteVO;
import com.teaclub.cloud.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OmsOrderMapper orderMapper;
    private final OmsOrderItemMapper itemMapper;
    private final OmsOrderStatusLogMapper statusLogMapper;
    private final OrderAmountCalculator calculator;
    private final com.teaclub.cloud.order.client.MemberInternalClient memberClient;
    private final com.teaclub.cloud.order.client.WalletInternalClient walletClient;
    private final com.teaclub.cloud.order.client.AuthInternalClient authClient;
    private final com.teaclub.cloud.order.saga.PaymentSagaService sagaService;
    private final com.teaclub.cloud.messaging.outbox.OutboxService outboxService;
    private final com.teaclub.cloud.order.client.CouponInternalClient couponClient;
    private final com.teaclub.cloud.order.client.PointsInternalClient pointsClient;

    @Override
    public OrderQuoteVO quote(OrderQuoteRequest request) {
        LoginUser current = UserContext.require();
        assertStoreAccessible(request.getStoreId());
        long couponDiscount = resolveCouponDiscount(request.getMemberCouponId(),
                request.getMemberId(), request.getStoreId(), request.getItems());
        long pointsDiscount = resolvePointsDiscount(request.getUsePoints(), request.getMemberId());
        return calculator.calculate(current.getBrandId(), request.getStoreId(), request.getMemberId(),
                request.getItems(), couponDiscount, pointsDiscount);
    }

    /**
     * 解析券抵扣金额：调 marketing-service 试算（券的真伪/归属/门槛/有效期全由营销服务判定）。
     *
     * <p>未传券、券不可用、或营销服务不可用一律返回 0 —— 让订单仍可正常下单，
     * 而不是因为一张券失败阻断整笔交易。
     */
    private long resolveCouponDiscount(Long memberCouponId, Long memberId, Long storeId,
                                       java.util.List<com.teaclub.cloud.order.dto.OrderItemRequest> items) {
        if (memberCouponId == null) {
            return 0L;
        }
        try {
            long baseAmount = calculator.calculate(
                    UserContext.require().getBrandId(), storeId, memberId, items, 0L, 0L).getTotalAmount();
            var resp = couponClient.quoteById(memberCouponId, memberId, storeId, baseAmount);
            if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                log.warn("券试算失败，按无券处理 memberCouponId={} code={}", memberCouponId,
                        resp == null ? null : resp.getCode());
                return 0L;
            }
            var data = resp.getData();
            Object avail = data.get("available");
            if (!(avail instanceof Boolean b) || !b) {
                log.info("券不可用，按无券处理 memberCouponId={} reason={}",
                        memberCouponId, data.get("reason"));
                return 0L;
            }
            Object d = data.get("discountAmount");
            return d instanceof Number n ? n.longValue() : 0L;
        } catch (Exception e) {
            log.warn("调用券试算异常，按无券处理 memberCouponId={} err={}", memberCouponId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 解析积分抵扣金额（分）：调 marketing-service 查可用积分余额，取 min(申请积分, 余额)。
     *
     * <p>1 积分 = 1 分钱，所以积分余额数值即为可抵扣的分值。试算只查余额<b>不扣</b>，
     * 真正扣减发生在支付成功。未传积分、散客、或营销服务不可用一律返回 0 ——
     * 不让积分抵扣失败阻断整笔交易。
     */
    private long resolvePointsDiscount(Long usePoints, Long memberId) {
        if (usePoints == null || usePoints <= 0 || memberId == null) {
            return 0L;
        }
        try {
            var resp = pointsClient.balance(memberId);
            if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                log.warn("积分余额查询失败，按不使用积分处理 memberId={} code={}", memberId,
                        resp == null ? null : resp.getCode());
                return 0L;
            }
            long balance = resp.getData();
            if (balance <= 0) {
                return 0L;
            }
            long applied = Math.min(usePoints, balance);
            log.info("积分试算 memberId={} 申请={} 可用={} 实际抵扣={}分",
                    memberId, usePoints, balance, applied);
            return applied;
        } catch (Exception e) {
            log.warn("调用积分余额查询异常，按不使用积分处理 memberId={} err={}",
                    memberId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 支付成功后扣减本单实际使用的积分（按订单号幂等；失败只记日志，不影响支付主流程）。
     *
     * <p>试算只查余额不扣，因此取消订单无需退积分；只有走到支付成功才真正扣。
     */
    private void deductPointsOnPaySuccess(OmsOrder order, String orderNo) {
        Long usePoints = order.getUsePoints();
        if (usePoints == null || usePoints <= 0 || order.getMemberId() == null) {
            return;
        }
        try {
            var resp = pointsClient.deduct(order.getMemberId(), usePoints, orderNo);
            if (resp == null || resp.getCode() != 0) {
                log.warn("积分扣减失败 orderNo={} memberId={} points={} code={}", orderNo,
                        order.getMemberId(), usePoints, resp == null ? null : resp.getCode());
            } else {
                log.info("积分已扣减 orderNo={} memberId={} points={}", orderNo,
                        order.getMemberId(), usePoints);
            }
        } catch (Exception e) {
            log.error("积分扣减异常 orderNo={} memberId={} points={}", orderNo,
                    order.getMemberId(), usePoints, e);
        }
    }

    /**
     * 支付成功后核销本单使用的优惠券（按订单号幂等；失败只记日志，不影响支付主流程）。
     * 余额支付与微信/Mock 支付两条路径都会走这里，避免券一直停在 LOCKED。
     */
    private void useCouponOnPaySuccess(OmsOrder order, String orderNo) {
        if (order.getMemberCouponId() == null || order.getCouponAmount() == null
                || order.getCouponAmount() <= 0) {
            return;
        }
        try {
            var resp = couponClient.useByCouponId(order.getMemberCouponId(),
                    orderNo, order.getCouponAmount());
            if (resp == null || resp.getCode() != 0) {
                log.warn("券核销失败 orderNo={} couponId={} code={}", orderNo,
                        order.getMemberCouponId(), resp == null ? null : resp.getCode());
            } else {
                log.info("券已核销 orderNo={} couponId={} 抵扣={}分",
                        orderNo, order.getMemberCouponId(), order.getCouponAmount());
            }
        } catch (Exception e) {
            log.error("券核销异常 orderNo={} couponId={}", orderNo, order.getMemberCouponId(), e);
        }
    }

    private void assertStoreAccessible(Long storeId) {
        if (storeId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "门店不能为空");
        }
        // 门店属 auth-service 域，order-service 不得直接访问 iam_db
        if (!storeExists(storeId)) {
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
        StoreScope.from(UserContext.require()).assertAccessible(storeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(OrderCreateRequest request) {
        LoginUser current = UserContext.require();
        assertStoreAccessible(request.getStoreId());

        // ===== 金额由服务端重算，忽略任何客户端金额 =====
        // 券抵扣金额由 marketing-service 试算（券的真伪/归属/门槛/有效期全由营销服务判定）
        long couponDiscount = resolveCouponDiscount(request.getMemberCouponId(),
                request.getMemberId(), request.getStoreId(), request.getItems());
        long pointsDiscount = resolvePointsDiscount(request.getUsePoints(), request.getMemberId());
        OrderQuoteVO quote = calculator.calculate(
                current.getBrandId(), request.getStoreId(), request.getMemberId(),
                request.getItems(), couponDiscount, pointsDiscount);

        String orderNo = BizNoGenerator.orderNo();
        OmsOrder order = new OmsOrder();
        order.setBrandId(current.getBrandId());
        order.setOrderNo(orderNo);
        order.setStoreId(request.getStoreId());
        order.setMemberId(request.getMemberId());
        order.setOrderSource(StringUtils.hasText(request.getOrderSource())
                ? request.getOrderSource() : "POS");
        order.setTotalAmount(quote.getTotalAmount());
        order.setDiscountAmount(quote.getDiscountAmount());
        order.setCouponAmount(quote.getCouponAmount());
        order.setLevelDiscountAmount(quote.getLevelDiscountAmount());
        order.setActivityDiscountAmount(quote.getActivityDiscountAmount());
        order.setPayableAmount(quote.getPayableAmount());
        order.setPaidAmount(0L);
        order.setRefundedAmount(0L);
        order.setBalancePrincipalAmount(0L);
        order.setBalanceGiftAmount(0L);
        order.setStatus(OrderStatus.UNPAID.name());
        order.setMemberCouponId(request.getMemberCouponId());
        // 实际使用的积分（已按余额/应付截断）：支付成功时按订单号幂等扣减
        long usePoints = quote.getPointsDiscountAmount() == null ? 0L : quote.getPointsDiscountAmount();
        order.setUsePoints(usePoints);
        order.setPointsEarned(0L);
        order.setPickupNo(generatePickupNo());
        order.setRemark(request.getRemark());
        order.setOperatorId(current.getUserId());
        orderMapper.insert(order);

        // 券锁定：把 UNUSED → LOCKED 并绑定本单号。锁不上则整单失败回滚
        // （不允许"金额里抵扣了但券没锁"，否则支付后无法核销，券会被重复使用）
        if (request.getMemberCouponId() != null && quote.getCouponAmount() != null
                && quote.getCouponAmount() > 0) {
            var lockResp = couponClient.lockByCouponId(request.getMemberCouponId(),
                    request.getMemberId(), orderNo);
            if (lockResp == null || lockResp.getCode() != 0 || !Boolean.TRUE.equals(lockResp.getData())) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "优惠券锁定失败（可能已被占用或已过期），请重新选择优惠券");
            }
            log.info("券已锁定 orderNo={} couponId={} 抵扣={}分",
                    orderNo, request.getMemberCouponId(), quote.getCouponAmount());
        }

        // 明细落库（快照）
        for (OrderItemVO item : quote.getItems()) {
            OmsOrderItem entity = new OmsOrderItem();
            entity.setBrandId(current.getBrandId());
            entity.setOrderId(order.getId());
            entity.setOrderNo(orderNo);
            entity.setStoreId(request.getStoreId());
            entity.setSpuId(item.getSpuId());
            entity.setSkuId(item.getSkuId());
            entity.setSpuName(item.getSpuName());
            entity.setSkuName(item.getSkuName());
            entity.setSpecJson(item.getSpecJson());
            entity.setImageUrl(item.getImageUrl());
            entity.setUnitPrice(item.getUnitPrice());
            entity.setQuantity(item.getQuantity());
            entity.setTotalAmount(item.getTotalAmount());
            entity.setDiscountAmount(item.getDiscountAmount());
            entity.setActualAmount(item.getActualAmount());
            entity.setRefundedQuantity(0);
            itemMapper.insert(entity);
        }

        logStatus(order, null, OrderStatus.UNPAID, current.getUserId(), "STAFF",
                "创建订单" + (usePoints > 0 ? "（积分抵扣 " + usePoints + " 分）" : ""));

        // 可选：立即余额支付
        if (Boolean.TRUE.equals(request.getPayWithBalance())) {
            if (request.getMemberId() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "散客订单不能使用余额支付");
            }
            return payByBalance(orderNo);
        }
        return detail(orderNo);
    }

    private String generatePickupNo() {
        return String.format("%03d", new Random().nextInt(1000));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO payByBalance(String orderNo) {
        OmsOrder order = orderMapper.selectByNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        StoreScope.from(UserContext.require()).assertAccessible(order.getStoreId());

        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            // 幂等：已支付直接返回
            return detail(orderNo);
        }
        if (!OrderStatus.UNPAID.name().equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单状态为 " + order.getStatus() + "，不可支付");
        }
        if (order.getMemberId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "散客订单不能使用余额支付");
        }
        if (order.getPayableAmount() == null || order.getPayableAmount() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单应付金额异常");
        }

        // ==== Saga: 扣款前登记（独立事务，进程崩溃后可被补偿任务发现）====
        com.teaclub.cloud.order.entity.OmsPaymentSaga saga =
                sagaService.begin(orderNo, order.getPayableAmount());

        // 钱包扣款（幂等键 = 订单号）
        Map<String, Object> consumeCmd = new java.util.HashMap<>();
        consumeCmd.put("brandId", order.getBrandId());
        consumeCmd.put("memberId", order.getMemberId());
        consumeCmd.put("storeId", order.getStoreId());
        consumeCmd.put("orderNo", orderNo);
        consumeCmd.put("payableAmount", order.getPayableAmount());
        consumeCmd.put("operatorId", order.getOperatorId());
        consumeCmd.put("operatorType", "STAFF");
        var consumeResp = walletClient.consumeOut(consumeCmd);
        if (consumeResp == null || consumeResp.getCode() != 0) {
            if (consumeResp == null) {
                throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "钱包服务不可用");
            }
            throw new BizException(consumeResp.getCode(), consumeResp.getMessage());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> changeData = consumeResp.getData();
        long principalDeduct = -toLong(changeData == null ? null : changeData.get("principalChange"));
        long giftDeduct = -toLong(changeData == null ? null : changeData.get("giftChange"));

        // 钱包已扣款成功，推进 Saga（此后若订单更新失败必须回冲）
        sagaService.markDeducted(saga.getId(), principalDeduct, giftDeduct);

        try {
            OmsOrder patch = new OmsOrder();
            patch.setId(order.getId());
            patch.setStatus(OrderStatus.PAID.name());
            patch.setPayChannel("BALANCE");
            patch.setPaidAmount(order.getPayableAmount());
            patch.setBalancePrincipalAmount(principalDeduct);
            patch.setBalanceGiftAmount(giftDeduct);
            patch.setPayTime(LocalDateTime.now());
            orderMapper.updateById(patch);

            logStatus(order, OrderStatus.UNPAID, OrderStatus.PAID,
                    UserContext.currentUserId(), "STAFF",
                    "余额支付：实付金 " + principalDeduct + " 分，赠送金 " + giftDeduct + " 分");

            // 订单已更新，Saga 完成
            sagaService.markCompleted(saga.getId());

            // 券核销 + 积分扣减：支付成功后处理（均按订单号幂等；失败只记日志，不回滚支付）
            useCouponOnPaySuccess(order, orderNo);
            deductPointsOnPaySuccess(order, orderNo);

            // 领域事件：订单已支付（与订单状态同一本地事务）
            Map<String, Object> paidEvent = new java.util.HashMap<>();
            paidEvent.put("orderNo", orderNo);
            paidEvent.put("brandId", order.getBrandId());
            paidEvent.put("storeId", order.getStoreId());
            paidEvent.put("memberId", order.getMemberId());
            paidEvent.put("levelId", fetchMemberLevelId(order.getMemberId()));
                        paidEvent.put("paidAmount", order.getPayableAmount());
            paidEvent.put("principalAmount", principalDeduct);
            paidEvent.put("giftAmount", giftDeduct);
            paidEvent.put("payChannel", "BALANCE");
            outboxService.enqueue(
                    com.teaclub.cloud.messaging.event.DomainEvents.ORDER_PAID,
                    orderNo, paidEvent);
        } catch (Exception e) {
            // 【Saga 补偿】订单更新失败 / 本地事务将回滚 -> 必须把已扣资金回冲，
            // 否则出现"钱扣了但订单永久待支付"。回冲用 COMP- 前缀保证幂等。
            log.error("订单更新失败，触发 Saga 补偿 orderNo={} 实付={} 赠送={}",
                    orderNo, principalDeduct, giftDeduct, e);
            Map<String, Object> comp = new java.util.HashMap<>();
            comp.put("brandId", order.getBrandId());
            comp.put("memberId", order.getMemberId());
            comp.put("storeId", order.getStoreId());
            comp.put("refundNo", "COMP-" + orderNo);
            comp.put("refundPrincipalAmount", principalDeduct);
            comp.put("refundGiftAmount", giftDeduct);
            comp.put("remark", "订单支付失败自动补偿");
            comp.put("operatorType", "SYSTEM");
            try {
                var compResp = walletClient.refundIn(comp);
                if (compResp != null && compResp.getCode() == 0) {
                    sagaService.markCompensated(saga.getId(), "订单更新失败已回冲: " + e.getMessage());
                    log.warn("Saga 补偿成功 orderNo={}", orderNo);
                } else {
                    sagaService.markFailed(saga.getId(), "回冲失败，待补偿任务处理");
                    log.error("Saga 补偿失败 orderNo={} resp={}", orderNo, compResp);
                }
            } catch (Exception ce) {
                // 回冲也失败：保留 RUNNING 状态，交给定时补偿任务重试
                sagaService.markFailed(saga.getId(), "回冲异常: " + ce.getMessage());
                log.error("Saga 补偿异常 orderNo={}", orderNo, ce);
            }
            throw e instanceof BizException ? (BizException) e
                    : new BizException(ResultCode.SYSTEM_ERROR, "订单支付失败：" + e.getMessage());
        }

        // 会员消费档案不再同步调用 —— 已改为消费 ORDER_PAID 事件（member-service 侧）
        return detail(orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO markPaying(String orderNo, String paymentNo, String payChannel) {
        OmsOrder order = orderMapper.selectByNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!OrderStatus.UNPAID.name().equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单状态不允许发起支付");
        }
        OmsOrder patch = new OmsOrder();
        patch.setId(order.getId());
        patch.setPaymentNo(paymentNo);
        patch.setPayChannel(payChannel);
        orderMapper.updateById(patch);
        return detail(orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processPaySuccess(String orderNo, String paymentNo) {
        OmsOrder order = orderMapper.selectByNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在: " + orderNo);
        }
        // 幂等：已支付直接返回
        if (OrderStatus.PAID.name().equals(order.getStatus())
                || OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            log.info("订单已支付，跳过重复处理 orderNo={}", orderNo);
            return;
        }
        if (!OrderStatus.UNPAID.name().equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单状态为 " + order.getStatus() + "，不可确认支付");
        }

        OmsOrder patch = new OmsOrder();
        patch.setId(order.getId());
        patch.setStatus(OrderStatus.PAID.name());
        patch.setPaymentNo(paymentNo);
        patch.setPaidAmount(order.getPayableAmount());
        patch.setPayTime(LocalDateTime.now());
        if (order.getPayChannel() == null) {
            patch.setPayChannel("WECHAT_JSAPI");
        }
        orderMapper.updateById(patch);

        logStatus(order, OrderStatus.UNPAID, OrderStatus.PAID,
                null, "SYSTEM", "微信支付成功确认 paymentNo=" + paymentNo);
        // 券核销 + 积分扣减：支付成功后处理（均按订单号幂等；失败只记日志，不影响支付确认）
        useCouponOnPaySuccess(order, orderNo);
        deductPointsOnPaySuccess(order, orderNo);
        // 会员消费档案改由 ORDER_PAID 事件驱动（member-service 侧消费）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String orderNo, String reason) {
        OmsOrder order = orderMapper.selectByNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        StoreScope.from(UserContext.require()).assertAccessible(order.getStoreId());

        if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            return;
        }
        if (!OrderStatus.UNPAID.name().equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "仅未支付订单可取消；已支付订单请走退款流程");
        }

        OmsOrder patch = new OmsOrder();
        patch.setId(order.getId());
        patch.setStatus(OrderStatus.CANCELLED.name());
        patch.setCancelTime(LocalDateTime.now());
        patch.setCancelReason(reason);
        orderMapper.updateById(patch);

        logStatus(order, OrderStatus.UNPAID, OrderStatus.CANCELLED,
                UserContext.currentUserId(), "STAFF", "取消订单：" + reason);
    }

    @Override
    public OrderVO detail(String orderNo) {
        OmsOrder order = orderMapper.selectByNo(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        StoreScope.from(UserContext.require()).assertAccessible(order.getStoreId());
        return toVO(order, true);
    }

    @Override
    public PageResult<OrderVO> page(OrderQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        if (scope.isEmptyLimited()) {
            return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
        }

        LambdaQueryWrapper<OmsOrder> wrapper = Wrappers.<OmsOrder>lambdaQuery()
                .eq(OmsOrder::getBrandId, current.getBrandId());
        if (!scope.isAll()) {
            wrapper.in(OmsOrder::getStoreId, scope.getStoreIds());
        }
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.like(OmsOrder::getOrderNo, query.getOrderNo().trim());
        }
        if (query.getStoreId() != null) {
            scope.assertAccessible(query.getStoreId());
            wrapper.eq(OmsOrder::getStoreId, query.getStoreId());
        }
        if (query.getMemberId() != null) {
            wrapper.eq(OmsOrder::getMemberId, query.getMemberId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(OmsOrder::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPayChannel())) {
            wrapper.eq(OmsOrder::getPayChannel, query.getPayChannel());
        }
        if (StringUtils.hasText(query.getOrderSource())) {
            wrapper.eq(OmsOrder::getOrderSource, query.getOrderSource());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(OmsOrder::getCreateTime, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(OmsOrder::getCreateTime, query.getEndDate().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(OmsOrder::getId);

        Page<OmsOrder> page = orderMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        List<OrderVO> records = page.getRecords().stream()
                .map(o -> toVO(o, false)).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String orderNo, String targetStatus, String remark) {
        OmsOrder order = orderMapper.selectByNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        StoreScope.from(UserContext.require()).assertAccessible(order.getStoreId());

        OrderStatus from = OrderStatus.of(order.getStatus());
        OrderStatus to;
        try {
            to = OrderStatus.of(targetStatus);
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "目标状态不合法: " + targetStatus);
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单不允许从 " + from.name() + " 流转到 " + to.name());
        }

        OmsOrder patch = new OmsOrder();
        patch.setId(order.getId());
        patch.setStatus(to.name());
        if (to == OrderStatus.COMPLETED) {
            patch.setCompleteTime(LocalDateTime.now());
        }
        orderMapper.updateById(patch);
        logStatus(order, from, to, UserContext.currentUserId(), "STAFF", remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(String orderNo, Long refundAmount, boolean fullyRefunded) {
        OmsOrder order = orderMapper.selectByNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        long newRefunded = (order.getRefundedAmount() == null ? 0 : order.getRefundedAmount()) + refundAmount;
        if (newRefunded > order.getPaidAmount()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "退款总额超过实付金额（已退 " + order.getRefundedAmount()
                            + " + 本次 " + refundAmount + " > 实付 " + order.getPaidAmount() + "）");
        }
        OmsOrder patch = new OmsOrder();
        patch.setId(order.getId());
        patch.setRefundedAmount(newRefunded);
        if (fullyRefunded || newRefunded >= order.getPaidAmount()) {
            patch.setStatus(OrderStatus.REFUNDED.name());
        } else {
            patch.setStatus(OrderStatus.REFUNDING.name());
        }
        orderMapper.updateById(patch);
        logStatus(order, OrderStatus.of(order.getStatus()),
                OrderStatus.of(patch.getStatus()), UserContext.currentUserId(), "SYSTEM",
                "退款 " + refundAmount + " 分");
    }

    private void logStatus(OmsOrder order, OrderStatus from, OrderStatus to,
                           Long operatorId, String operatorType, String remark) {
        OmsOrderStatusLog entity = new OmsOrderStatusLog();
        entity.setBrandId(order.getBrandId());
        entity.setOrderId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        entity.setFromStatus(from == null ? null : from.name());
        entity.setToStatus(to.name());
        entity.setOperatorId(operatorId);
        entity.setOperatorType(operatorType);
        entity.setRemark(remark);
        entity.setCreateTime(LocalDateTime.now());
        statusLogMapper.insert(entity);
    }

    private OrderVO toVO(OmsOrder o, boolean withItems) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setStoreId(o.getStoreId());
        vo.setStoreName(fetchStoreName(o.getStoreId()));
        vo.setMemberId(o.getMemberId());
        if (o.getMemberId() != null) {
            Map<String, Object> m = fetchMember(o.getMemberId());
            if (m != null) {
                vo.setMemberName(str(m.get("nickname")));
                vo.setMemberPhone(str(m.get("phone")));
            }
        }
        vo.setOrderSource(o.getOrderSource());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setDiscountAmount(o.getDiscountAmount());
        vo.setCouponAmount(o.getCouponAmount());
        vo.setLevelDiscountAmount(o.getLevelDiscountAmount());
        vo.setActivityDiscountAmount(o.getActivityDiscountAmount());
        vo.setPayableAmount(o.getPayableAmount());
        vo.setPaidAmount(o.getPaidAmount());
        vo.setRefundedAmount(o.getRefundedAmount());
        vo.setBalancePrincipalAmount(o.getBalancePrincipalAmount());
        vo.setBalanceGiftAmount(o.getBalanceGiftAmount());
        vo.setStatus(o.getStatus());
        vo.setStatusLabel(labelOf(o.getStatus()));
        vo.setPayChannel(o.getPayChannel());
        vo.setPaymentNo(o.getPaymentNo());
        vo.setMemberCouponId(o.getMemberCouponId());
        vo.setPointsEarned(o.getPointsEarned());
        vo.setPickupNo(o.getPickupNo());
        vo.setPayTime(o.getPayTime());
        vo.setCompleteTime(o.getCompleteTime());
        vo.setCancelTime(o.getCancelTime());
        vo.setCancelReason(o.getCancelReason());
        vo.setRemark(o.getRemark());
        vo.setCreateTime(o.getCreateTime());

        if (withItems) {
            List<OmsOrderItem> items = itemMapper.selectList(Wrappers.<OmsOrderItem>lambdaQuery()
                    .eq(OmsOrderItem::getOrderId, o.getId())
                    .orderByAsc(OmsOrderItem::getId));
            vo.setItems(items.stream().map(i -> {
                OrderItemVO iv = new OrderItemVO();
                iv.setId(i.getId());
                iv.setSpuId(i.getSpuId());
                iv.setSkuId(i.getSkuId());
                iv.setSpuName(i.getSpuName());
                iv.setSkuName(i.getSkuName());
                iv.setSpecJson(i.getSpecJson());
                iv.setImageUrl(i.getImageUrl());
                iv.setUnitPrice(i.getUnitPrice());
                iv.setQuantity(i.getQuantity());
                iv.setTotalAmount(i.getTotalAmount());
                iv.setDiscountAmount(i.getDiscountAmount());
                iv.setActualAmount(i.getActualAmount());
                iv.setRefundedQuantity(i.getRefundedQuantity());
                return iv;
            }).collect(Collectors.toList()));
        }
        return vo;
    }

    private String labelOf(String status) {
        try {
            return OrderStatus.valueOf(status).getLabel();
        } catch (Exception e) {
            return status;
        }
    }
    // ==================== 跨服务调用辅助 ====================

    /**
     * 门店是否存在（auth-service）。
     *
     * <p>门店属 auth-service 域，order-service 不得直接访问 iam_db。
     */
    private boolean storeExists(Long storeId) {
        try {
            var resp = authClient.store(storeId);
            return resp != null && resp.getCode() == 0 && resp.getData() != null;
        } catch (Exception e) {
            log.error("查询门店失败，按不存在处理: storeId={}", storeId, e);
            return false;
        }
    }

    /**
     * 门店名称（auth-service）。
     */
    private String fetchStoreName(Long storeId) {
        if (storeId == null) {
            return null;
        }
        try {
            var resp = authClient.store(storeId);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                return str(resp.getData().get("store_name"));
            }
        } catch (Exception e) {
            log.warn("获取门店名称失败: storeId={}", storeId, e);
        }
        return null;
    }

    /**
     * 会员信息（member-service）。
     */
    private Map<String, Object> fetchMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        try {
            var resp = memberClient.userInfo(memberId);
            if (resp != null && resp.getCode() == 0) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.warn("获取会员信息失败: memberId={}", memberId, e);
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    /**
     * 查会员当前等级 ID（事件载荷携带，供下游按等级倍率发积分）。
     * 失败返回 null —— 等级查询失败不应阻断支付主流程。
     */
    private Long fetchMemberLevelId(Long memberId) {
        if (memberId == null) {
            return null;
        }
        try {
            var resp = memberClient.memberBrief(memberId);
            if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                return null;
            }
            Object v = resp.getData().get("levelId");
            return v instanceof Number n ? n.longValue() : null;
        } catch (Exception e) {
            log.warn("查询会员等级失败（不影响支付）: memberId={}", memberId, e);
            return null;
        }
    }
}
