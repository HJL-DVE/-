package com.teaclub.payment.service.impl;

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
import com.teaclub.order.entity.OmsOrder;
import com.teaclub.order.mapper.OmsOrderMapper;
import com.teaclub.order.service.OrderService;
import com.teaclub.payment.dto.PayCreateRequest;
import com.teaclub.payment.dto.RefundApplyRequest;
import com.teaclub.payment.entity.PayNotifyLog;
import com.teaclub.payment.entity.PayPaymentOrder;
import com.teaclub.payment.entity.PayRefundOrder;
import com.teaclub.payment.gateway.*;
import com.teaclub.payment.mapper.PayNotifyLogMapper;
import com.teaclub.payment.mapper.PayPaymentOrderMapper;
import com.teaclub.payment.mapper.PayRefundOrderMapper;
import com.teaclub.payment.service.PaymentService;
import com.teaclub.payment.vo.PayCreateVO;
import com.teaclub.payment.vo.PaymentVO;
import com.teaclub.payment.vo.RefundVO;
import com.teaclub.wallet.dto.RefundInCmd;
import com.teaclub.wallet.entity.WalRechargeOrder;
import com.teaclub.wallet.mapper.WalRechargeOrderMapper;
import com.teaclub.wallet.service.RechargeService;
import com.teaclub.wallet.service.WalletService;
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
public class PaymentServiceImpl implements PaymentService {

    private final PayPaymentOrderMapper paymentMapper;
    private final PayRefundOrderMapper refundMapper;
    private final PayNotifyLogMapper notifyMapper;
    private final PaymentProvider provider;
    private final OrderService orderService;
    private final RechargeService rechargeService;
    private final WalletService walletService;
    private final OmsOrderMapper orderMapper;
    private final WalRechargeOrderMapper rechargeMapper;

    // ==================== 创建支付 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayCreateVO createPayment(PayCreateRequest request) {
        LoginUser current = UserContext.require();

        // 已有支付单则复用（幂等）
        PayPaymentOrder existing = paymentMapper.selectByBizForUpdate(request.getBizType(), request.getBizNo());
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该业务单已支付成功，无需重复支付");
        }

        // 金额由服务端按业务单确定，不接受客户端传入
        long amount;
        Long storeId;
        Long memberId;
        if ("ORDER".equals(request.getBizType())) {
            OmsOrder order = orderMapper.selectByNo(request.getBizNo());
            if (order == null) {
                throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
            }
            if (!"UNPAID".equals(order.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "订单状态为 " + order.getStatus() + "，不可发起支付");
            }
            StoreScope.from(current).assertAccessible(order.getStoreId());
            amount = order.getPayableAmount();
            storeId = order.getStoreId();
            memberId = order.getMemberId();
        } else {
            WalRechargeOrder recharge = rechargeMapper.selectByNo(request.getBizNo());
            if (recharge == null) {
                throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在");
            }
            if (!"PENDING".equals(recharge.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "充值订单状态为 " + recharge.getStatus() + "，不可发起支付");
            }
            StoreScope.from(current).assertAccessible(recharge.getStoreId());
            amount = recharge.getRechargeAmount();
            storeId = recharge.getStoreId();
            memberId = recharge.getMemberId();
        }
        if (amount <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "支付金额异常");
        }

        PayPaymentOrder payment = existing;
        if (payment == null) {
            payment = new PayPaymentOrder();
            payment.setBrandId(current.getBrandId());
            payment.setPaymentNo(BizNoGenerator.paymentNo());
            payment.setBizType(request.getBizType());
            payment.setBizNo(request.getBizNo());
            payment.setStoreId(storeId);
            payment.setMemberId(memberId);
            payment.setChannel(request.getChannel());
            payment.setProvider(provider.providerName());
            payment.setAmount(amount);
            payment.setCurrency("CNY");
            payment.setStatus("CREATED");
            payment.setNotifyReceived(0);
            payment.setExpireTime(LocalDateTime.now().plusMinutes(30));
            paymentMapper.insert(payment);
        }

        // 调用渠道下单
        PayCreateCmd cmd = PayCreateCmd.builder()
                .paymentNo(payment.getPaymentNo())
                .amount(payment.getAmount())
                .description(request.getBizType() + ":" + request.getBizNo())
                .openid(request.getOpenid())
                .clientIp(request.getClientIp())
                .expireSeconds(1800L)
                .build();

        PayCreateResult result;
        if ("WECHAT_NATIVE".equals(request.getChannel())
                || "MOCK_NATIVE".equals(request.getChannel())) {
            result = provider.createNativePay(cmd);
        } else {
            result = provider.createJsapiPay(cmd);
        }

        PayPaymentOrder patch = new PayPaymentOrder();
        patch.setId(payment.getId());
        if (result.isSuccess()) {
            patch.setStatus("PAYING");
            patch.setPrepayId(result.getPrepayId());
            patch.setCodeUrl(result.getCodeUrl());
        } else {
            patch.setStatus("FAILED");
            patch.setFailReason(result.getFailReason());
        }
        paymentMapper.updateById(patch);

        // 业务单标记支付中
        if ("ORDER".equals(request.getBizType())) {
            orderService.markPaying(request.getBizNo(), payment.getPaymentNo(), request.getChannel());
        }

        PayCreateVO vo = new PayCreateVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setProvider(payment.getProvider());
        vo.setChannel(request.getChannel());
        vo.setAmount(payment.getAmount());
        vo.setStatus(patch.getStatus());
        vo.setInvokeParams(result.getInvokeParams());
        vo.setCodeUrl(result.getCodeUrl());
        if ("MOCK".equals(payment.getProvider())) {
            vo.setTip("当前为 Mock 支付渠道，请调用 POST /api/payment/mock/notify 模拟支付成功");
        }
        return vo;
    }

    @Override
    public PaymentVO queryPayment(String paymentNo) {
        PayPaymentOrder payment = paymentMapper.selectByNo(paymentNo);
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在");
        }
        StoreScope.from(UserContext.require()).assertAccessible(payment.getStoreId());

        PayQueryResult qr = provider.queryByOutTradeNo(paymentNo);
        if ("SUCCESS".equals(qr.getTradeState())) {
            handlePaySuccess(paymentNo, qr.getTransactionId(), qr.getAmount(), provider.providerName());
            payment = paymentMapper.selectByNo(paymentNo);
        } else if ("UNKNOWN".equals(qr.getTradeState())) {
            log.info("查单未决 paymentNo={} reason={}", paymentNo, qr.getFailReason());
        }
        PaymentVO vo = toPaymentVO(payment);
        if (qr.getTradeState() != null && !"SUCCESS".equals(qr.getTradeState())) {
            vo.setFailReason(qr.getFailReason());
        }
        return vo;
    }

    // ==================== 支付成功处理（幂等核心） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaySuccess(String paymentNo, String transactionId,
                                 Long notifyAmount, String providerName) {
        PayPaymentOrder payment = paymentMapper.selectByNoForUpdate(paymentNo);
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在: " + paymentNo);
        }

        // ---- 幂等：已成功直接返回 ----
        if ("SUCCESS".equals(payment.getStatus())) {
            log.info("支付单已成功，忽略重复通知 paymentNo={}", paymentNo);
            return;
        }

        // ---- 金额校验：通知金额必须与服务端记录逐分相等 ----
        if (notifyAmount != null && !notifyAmount.equals(payment.getAmount())) {
            log.error("支付金额不符 paymentNo={} 通知={} 服务端={}",
                    paymentNo, notifyAmount, payment.getAmount());
            throw new BizException(ResultCode.BAD_REQUEST,
                    "支付金额与订单金额不一致：通知 " + notifyAmount + " 分，服务端 " + payment.getAmount() + " 分");
        }

        if (!"PAYING".equals(payment.getStatus()) && !"CREATED".equals(payment.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "支付单状态为 " + payment.getStatus() + "，不可确认成功");
        }

        // ---- 更新支付单 ----
        PayPaymentOrder patch = new PayPaymentOrder();
        patch.setId(payment.getId());
        patch.setStatus("SUCCESS");
        patch.setTransactionId(transactionId);
        patch.setSuccessTime(LocalDateTime.now());
        patch.setNotifyReceived(1);
        paymentMapper.updateById(patch);

        // ---- 分发到业务 ----
        if ("ORDER".equals(payment.getBizType())) {
            orderService.processPaySuccess(payment.getBizNo(), paymentNo);
        } else if ("RECHARGE".equals(payment.getBizType())) {
            rechargeService.processPaySuccess(payment.getBizNo(), paymentNo);
        } else {
            throw new BizException(ResultCode.BAD_REQUEST, "未知业务类型: " + payment.getBizType());
        }

        log.info("支付成功处理完成 paymentNo={} bizType={} bizNo={}",
                paymentNo, payment.getBizType(), payment.getBizNo());
    }

    // ==================== 退款 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO applyRefund(RefundApplyRequest request) {
        LoginUser current = UserContext.require();

        OmsOrder order = orderMapper.selectByNoForUpdate(request.getOrderNo());
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        StoreScope.from(current).assertAccessible(order.getStoreId());

        if (!"PAID".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())
                && !"REFUNDING".equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单状态为 " + order.getStatus() + "，不可退款");
        }

        // 退款金额校验：不超过 实付 - 已退
        long paid = order.getPaidAmount() == null ? 0 : order.getPaidAmount();
        long refunded = order.getRefundedAmount() == null ? 0 : order.getRefundedAmount();
        long refundable = paid - refunded;
        if (request.getRefundAmount() > refundable) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "退款金额超过可退余额：可退 " + refundable + " 分，申请 " + request.getRefundAmount() + " 分");
        }

        // 按原支付方式分配资金构成
        String refundNo = BizNoGenerator.refundNo();
        PayRefundOrder refund = new PayRefundOrder();
        refund.setBrandId(current.getBrandId());
        refund.setRefundNo(refundNo);
        // 余额支付不会产生 pay_payment_order 记录，但 pay_refund_order.payment_no 为 NOT NULL。
        // 这里使用可追溯的合成引用（BAL- + 订单号），保证退款单与原订单可关联。
        String paymentRef = order.getPaymentNo();
        if (paymentRef == null || paymentRef.isBlank()) {
            paymentRef = ("BALANCE".equals(order.getPayChannel()) ? "BAL-" : "ORD-") + order.getOrderNo();
        }
        refund.setPaymentNo(paymentRef);
        refund.setBizType("ORDER");
        refund.setBizNo(order.getOrderNo());
        refund.setStoreId(order.getStoreId());
        refund.setMemberId(order.getMemberId());
        refund.setReason(request.getReason());
        refund.setApplyUserId(current.getUserId());
        refund.setRefundAmount(request.getRefundAmount());

        if ("BALANCE".equals(order.getPayChannel())) {
            refund.setChannel("BALANCE");
            // 按原扣款构成分摊：用减法保证两项之和精确等于退款额
            long origPrincipal = order.getBalancePrincipalAmount() == null ? 0 : order.getBalancePrincipalAmount();
            long origGift = order.getBalanceGiftAmount() == null ? 0 : order.getBalanceGiftAmount();
            long[] alloc = allocateRefund(origPrincipal, origGift, paid, request.getRefundAmount());
            refund.setRefundPrincipalAmount(alloc[0]);
            refund.setRefundGiftAmount(alloc[1]);
            refund.setRefundCashAmount(0L);
        } else {
            refund.setChannel("WECHAT");
            refund.setRefundPrincipalAmount(0L);
            refund.setRefundGiftAmount(0L);
            refund.setRefundCashAmount(request.getRefundAmount());
        }
        refund.setStatus("CREATED");
        refundMapper.insert(refund);

        // 执行退款
        executeRefund(refund, order);
        return toRefundVO(refundMapper.selectByNoForUpdate(refundNo));
    }

    /**
     * 退款资金构成分摊。
     *
     * <p>实付部分按比例向下取整，赠送部分用减法兜底，
     * 保证 {@code principalPart + giftPart == refundAmount} 精确成立。
     */
    static long[] allocateRefund(long origPrincipal, long origGift, long origPaid, long refundAmount) {
        if (origPaid <= 0) {
            return new long[]{0, 0};
        }
        if (refundAmount >= origPaid) {
            return new long[]{origPrincipal, origGift};
        }
        long principalPart = java.math.BigDecimal.valueOf(origPrincipal)
                .multiply(java.math.BigDecimal.valueOf(refundAmount))
                .divide(java.math.BigDecimal.valueOf(origPaid), 0, java.math.RoundingMode.DOWN)
                .longValueExact();
        if (principalPart > origPrincipal) {
            principalPart = origPrincipal;
        }
        long giftPart = refundAmount - principalPart;
        if (giftPart < 0) {
            giftPart = 0;
            principalPart = refundAmount;
        }
        return new long[]{principalPart, giftPart};
    }

    private void executeRefund(PayRefundOrder refund, OmsOrder order) {
        boolean fullyRefunded = (order.getRefundedAmount() == null ? 0 : order.getRefundedAmount())
                + refund.getRefundAmount() >= order.getPaidAmount();

        try {
            if ("BALANCE".equals(refund.getChannel())) {
                // 余额退款：回冲钱包，按原资金构成
                walletService.refundIn(RefundInCmd.builder()
                        .brandId(refund.getBrandId())
                        .memberId(refund.getMemberId())
                        .storeId(refund.getStoreId())
                        .refundNo(refund.getRefundNo())
                        .refundPrincipalAmount(refund.getRefundPrincipalAmount())
                        .refundGiftAmount(refund.getRefundGiftAmount())
                        .remark(refund.getReason())
                        .operatorId(refund.getApplyUserId())
                        .operatorType("STAFF")
                        .build());

                markRefundSuccess(refund, null);
                orderService.applyRefund(order.getOrderNo(), refund.getRefundAmount(), fullyRefunded);
            } else {
                // 微信退款：调渠道
                RefundResult result = provider.createRefund(RefundCreateCmd.builder()
                        .refundNo(refund.getRefundNo())
                        .paymentNo(refund.getPaymentNo())
                        .totalAmount(order.getPaidAmount())
                        .refundAmount(refund.getRefundAmount())
                        .reason(refund.getReason())
                        .build());

                if (result.isSuccess()) {
                    if ("SUCCESS".equals(result.getStatus())) {
                        markRefundSuccess(refund, result.getRefundId());
                        orderService.applyRefund(order.getOrderNo(), refund.getRefundAmount(), fullyRefunded);
                    } else {
                        // PROCESSING：等待退款通知
                        PayRefundOrder patch = new PayRefundOrder();
                        patch.setId(refund.getId());
                        patch.setStatus("PROCESSING");
                        patch.setWxRefundId(result.getRefundId());
                        refundMapper.updateById(patch);
                    }
                } else {
                    markRefundFailed(refund, result.getFailReason());
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "微信退款受理失败：" + result.getFailReason());
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("退款执行异常 refundNo={}", refund.getRefundNo(), e);
            markRefundFailed(refund, e.getMessage());
            throw new BizException(ResultCode.SYSTEM_ERROR, "退款执行失败：" + e.getMessage());
        }
    }

    private void markRefundSuccess(PayRefundOrder refund, String wxRefundId) {
        PayRefundOrder patch = new PayRefundOrder();
        patch.setId(refund.getId());
        patch.setStatus("SUCCESS");
        patch.setWxRefundId(wxRefundId);
        patch.setSuccessTime(LocalDateTime.now());
        refundMapper.updateById(patch);
    }

    private void markRefundFailed(PayRefundOrder refund, String reason) {
        PayRefundOrder patch = new PayRefundOrder();
        patch.setId(refund.getId());
        patch.setStatus("FAILED");
        patch.setFailReason(reason);
        refundMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRefundSuccess(String refundNo, String wxRefundId) {
        PayRefundOrder refund = refundMapper.selectByNoForUpdate(refundNo);
        if (refund == null) {
            throw new BizException(ResultCode.NOT_FOUND, "退款单不存在: " + refundNo);
        }
        if ("SUCCESS".equals(refund.getStatus())) {
            log.info("退款单已成功，忽略重复通知 refundNo={}", refundNo);
            return;
        }
        markRefundSuccess(refund, wxRefundId);

        if ("ORDER".equals(refund.getBizType())) {
            OmsOrder order = orderMapper.selectByNo(refund.getBizNo());
            if (order != null) {
                boolean fully = (order.getRefundedAmount() == null ? 0 : order.getRefundedAmount())
                        + refund.getRefundAmount() >= order.getPaidAmount();
                orderService.applyRefund(order.getOrderNo(), refund.getRefundAmount(), fully);
            }
        }
        log.info("退款成功处理完成 refundNo={}", refundNo);
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<PaymentVO> pagePayment(Integer pageNo, Integer pageSize,
                                             String status, String bizType) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        if (scope.isEmptyLimited()) {
            return new PageResult<>(Collections.emptyList(), 0, 1, 20);
        }
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<PayPaymentOrder> wrapper = Wrappers.<PayPaymentOrder>lambdaQuery()
                .eq(PayPaymentOrder::getBrandId, current.getBrandId());
        if (!scope.isAll()) {
            wrapper.in(PayPaymentOrder::getStoreId, scope.getStoreIds());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PayPaymentOrder::getStatus, status);
        }
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(PayPaymentOrder::getBizType, bizType);
        }
        wrapper.orderByDesc(PayPaymentOrder::getId);

        Page<PayPaymentOrder> page = paymentMapper.selectPage(new Page<>(no, size), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toPaymentVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<RefundVO> pageRefund(Integer pageNo, Integer pageSize, String status) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        if (scope.isEmptyLimited()) {
            return new PageResult<>(Collections.emptyList(), 0, 1, 20);
        }
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<PayRefundOrder> wrapper = Wrappers.<PayRefundOrder>lambdaQuery()
                .eq(PayRefundOrder::getBrandId, current.getBrandId());
        if (!scope.isAll()) {
            wrapper.in(PayRefundOrder::getStoreId, scope.getStoreIds());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PayRefundOrder::getStatus, status);
        }
        wrapper.orderByDesc(PayRefundOrder::getId);

        Page<PayRefundOrder> page = refundMapper.selectPage(new Page<>(no, size), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toRefundVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<Map<String, Object>> pageNotify(Integer pageNo, Integer pageSize, String status) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<PayNotifyLog> wrapper = Wrappers.<PayNotifyLog>lambdaQuery();
        if (StringUtils.hasText(status)) {
            wrapper.eq(PayNotifyLog::getProcessStatus, status);
        }
        wrapper.orderByDesc(PayNotifyLog::getId);

        Page<PayNotifyLog> page = notifyMapper.selectPage(new Page<>(no, size), wrapper);
        List<Map<String, Object>> records = page.getRecords().stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("provider", n.getProvider());
            m.put("notifyId", n.getNotifyId());
            m.put("notifyType", n.getNotifyType());
            m.put("eventType", n.getEventType());
            m.put("outTradeNo", n.getOutTradeNo());
            m.put("verifyResult", n.getVerifyResult());
            m.put("processStatus", n.getProcessStatus());
            m.put("processMsg", n.getProcessMsg());
            m.put("createTime", n.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private PaymentVO toPaymentVO(PayPaymentOrder p) {
        PaymentVO vo = new PaymentVO();
        vo.setId(p.getId());
        vo.setPaymentNo(p.getPaymentNo());
        vo.setBizType(p.getBizType());
        vo.setBizNo(p.getBizNo());
        vo.setStoreId(p.getStoreId());
        vo.setMemberId(p.getMemberId());
        vo.setChannel(p.getChannel());
        vo.setProvider(p.getProvider());
        vo.setAmount(p.getAmount());
        vo.setStatus(p.getStatus());
        vo.setTransactionId(p.getTransactionId());
        vo.setCodeUrl(p.getCodeUrl());
        vo.setSuccessTime(p.getSuccessTime());
        vo.setFailReason(p.getFailReason());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }

    private RefundVO toRefundVO(PayRefundOrder r) {
        RefundVO vo = new RefundVO();
        vo.setId(r.getId());
        vo.setRefundNo(r.getRefundNo());
        vo.setPaymentNo(r.getPaymentNo());
        vo.setBizType(r.getBizType());
        vo.setBizNo(r.getBizNo());
        vo.setStoreId(r.getStoreId());
        vo.setMemberId(r.getMemberId());
        vo.setChannel(r.getChannel());
        vo.setRefundAmount(r.getRefundAmount());
        vo.setRefundPrincipalAmount(r.getRefundPrincipalAmount());
        vo.setRefundGiftAmount(r.getRefundGiftAmount());
        vo.setRefundCashAmount(r.getRefundCashAmount());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setWxRefundId(r.getWxRefundId());
        vo.setSuccessTime(r.getSuccessTime());
        vo.setFailReason(r.getFailReason());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }
}
