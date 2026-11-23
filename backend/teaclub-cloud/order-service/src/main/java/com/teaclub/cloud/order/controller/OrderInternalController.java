package com.teaclub.cloud.order.controller;

import com.teaclub.cloud.order.entity.OmsOrder;
import com.teaclub.cloud.order.enums.OrderStatus;
import com.teaclub.cloud.order.mapper.OmsOrderMapper;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * order-service 内部接口（仅供其他微服务调用）。
 *
 * <p>访问控制：由 `InternalTokenFilter` 校验 `X-Internal-Token`。
 *
 * <p>供 payment-service 使用：查订单、标记支付中/已支付、退款申请。
 * 供 member-service 使用：查门店消费过的会员 ID（可见口径 B）。
 */
@Tag(name = "订单内部接口", description = "服务间调用专用，需携带内部凭证")
@RestController
@RequestMapping("/api/order/internal")
@RequiredArgsConstructor
@Slf4j
public class OrderInternalController {

    private final OmsOrderMapper orderMapper;
    private final com.teaclub.cloud.messaging.outbox.OutboxService outboxService;
    private final com.teaclub.cloud.order.service.OrderWalletReconciliationService reconciliationService;
    private final com.teaclub.cloud.order.client.PointsInternalClient pointsClient;
    private final com.teaclub.cloud.order.client.CouponInternalClient couponClient;

    @Operation(summary = "按订单号查询订单（内部调用）")
    @GetMapping("/order/{orderNo}")
    public Result<Map<String, Object>> order(@PathVariable("orderNo") String orderNo) {
        OmsOrder order = orderMapper.selectByNo(orderNo);
        if (order == null) {
            return Result.ok(null);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("order_no", order.getOrderNo());
        data.put("brand_id", order.getBrandId());
        data.put("store_id", order.getStoreId());
        data.put("member_id", order.getMemberId());
        data.put("status", order.getStatus());
        data.put("total_amount", order.getTotalAmount());
        data.put("payable_amount", order.getPayableAmount());
        data.put("paid_amount", order.getPaidAmount());
        data.put("refunded_amount", order.getRefundedAmount());
        data.put("pay_channel", order.getPayChannel());
        data.put("payment_no", order.getPaymentNo());
        data.put("balance_principal_amount", order.getBalancePrincipalAmount());
        data.put("balance_gift_amount", order.getBalanceGiftAmount());
        log.info("内部调用查询订单: orderNo={}", orderNo);
        return Result.ok(data);
    }

    @Operation(summary = "标记订单支付中（内部调用）")
    @PostMapping("/order/{orderNo}/mark-paying")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> markPaying(@PathVariable("orderNo") String orderNo,
                                   @RequestBody Map<String, Object> body) {
        OmsOrder existing = orderMapper.selectByNo(orderNo);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OmsOrder patch = new OmsOrder();
        patch.setId(existing.getId());
        patch.setPaymentNo(str(body.get("paymentNo")));
        patch.setPayChannel(str(body.get("payChannel")));
        orderMapper.updateById(patch);
        log.info("内部调用标记订单支付中: orderNo={}", orderNo);
        return Result.ok();
    }

    @Operation(summary = "标记订单已支付（内部调用）")
    @PostMapping("/order/{orderNo}/mark-paid")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> markPaid(@PathVariable("orderNo") String orderNo,
                                 @RequestBody Map<String, Object> body) {
        OmsOrder existing = orderMapper.selectByNo(orderNo);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OmsOrder patch = new OmsOrder();
        patch.setId(existing.getId());
        patch.setStatus(OrderStatus.PAID.name());
        patch.setPaymentNo(str(body.get("paymentNo")));
        patch.setPayChannel(str(body.get("payChannel")));
        patch.setPaidAmount(toLong(body.get("paidAmount")));
        patch.setPayTime(LocalDateTime.now());
        orderMapper.updateById(patch);

        // 领域事件：订单已支付（微信支付渠道）
        Map<String, Object> paidEvent = new LinkedHashMap<>();
        paidEvent.put("orderNo", orderNo);
        paidEvent.put("brandId", existing.getBrandId());
        paidEvent.put("storeId", existing.getStoreId());
        paidEvent.put("memberId", existing.getMemberId());
        paidEvent.put("paidAmount", toLong(body.get("paidAmount")));
        paidEvent.put("payChannel", str(body.get("payChannel")));
        paidEvent.put("paymentNo", str(body.get("paymentNo")));
        outboxService.enqueue(
                com.teaclub.cloud.messaging.event.DomainEvents.ORDER_PAID, orderNo, paidEvent);

        // 券核销：支付成功后把 LOCKED 券置 USED（按订单号幂等；失败只记日志）
        if (existing.getMemberCouponId() != null && existing.getCouponAmount() != null
                && existing.getCouponAmount() > 0) {
            try {
                var resp = couponClient.useByCouponId(existing.getMemberCouponId(),
                        orderNo, existing.getCouponAmount());
                if (resp == null || resp.getCode() != 0) {
                    log.warn("券核销失败 orderNo={} couponId={} code={}", orderNo,
                            existing.getMemberCouponId(), resp == null ? null : resp.getCode());
                }
            } catch (Exception e) {
                log.error("券核销异常 orderNo={} couponId={}", orderNo,
                        existing.getMemberCouponId(), e);
            }
        }

        // 积分抵扣：支付成功后按订单号幂等扣减（失败只记日志，不影响支付确认）
        Long usePoints = existing.getUsePoints();
        if (usePoints != null && usePoints > 0 && existing.getMemberId() != null) {
            try {
                var resp = pointsClient.deduct(existing.getMemberId(), usePoints, orderNo);
                if (resp == null || resp.getCode() != 0) {
                    log.warn("积分扣减失败 orderNo={} memberId={} points={} code={}", orderNo,
                            existing.getMemberId(), usePoints, resp == null ? null : resp.getCode());
                } else {
                    log.info("积分已扣减 orderNo={} memberId={} points={}", orderNo,
                            existing.getMemberId(), usePoints);
                }
            } catch (Exception e) {
                log.error("积分扣减异常 orderNo={} memberId={} points={}", orderNo,
                        existing.getMemberId(), usePoints, e);
            }
        }

        log.info("内部调用标记订单已支付: orderNo={}", orderNo);
        return Result.ok();
    }

    @Operation(summary = "订单退款申请（内部调用）")
    @PostMapping("/order/{orderNo}/apply-refund")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> applyRefund(@PathVariable("orderNo") String orderNo,
                                     @RequestBody Map<String, Object> body) {
        OmsOrder order = orderMapper.selectByNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        Long amount = toLong(body.get("refundAmount"));
        boolean fully = Boolean.TRUE.equals(body.get("fullyRefunded"));
        OmsOrder patch = new OmsOrder();
        patch.setId(order.getId());
        patch.setStatus(fully ? OrderStatus.REFUNDED.name() : OrderStatus.REFUNDING.name());
        patch.setRefundedAmount((order.getRefundedAmount() == null ? 0 : order.getRefundedAmount())
                + (amount == null ? 0 : amount));
        orderMapper.updateById(patch);
        log.info("内部调用订单退款申请: orderNo={} amount={} fully={}", orderNo, amount, fully);
        return Result.ok();
    }

    @Operation(summary = "订单-钱包跨服务对账（内部调用）")
    @org.springframework.web.bind.annotation.GetMapping("/reconcile")
    public Result<Map<String, Object>> reconcile(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(reconciliationService.reconcile(days));
    }

    @Operation(summary = "查询门店消费过的会员 ID（可见口径 B）")
    @GetMapping("/member-ids")
    public Result<List<Long>> memberIdsByStoreIds(@RequestParam Long brandId,
                                                  @RequestParam List<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Result.ok(java.util.Collections.emptyList());
        }
        List<Long> ids = orderMapper.selectMemberIdsByStoreIds(brandId, storeIds);
        log.info("查询门店消费过的会员: brandId={} stores={} count={}", brandId, storeIds, ids.size());
        return Result.ok(ids);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }
}
