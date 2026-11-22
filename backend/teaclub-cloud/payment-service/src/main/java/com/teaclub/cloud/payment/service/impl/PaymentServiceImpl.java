package com.teaclub.cloud.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.payment.client.OrderInternalClient;
import com.teaclub.cloud.payment.client.WalletInternalClient;
import java.util.LinkedHashMap;
import java.util.Map;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.payment.dto.PayCreateRequest;
import com.teaclub.cloud.payment.dto.RechargeRefundRequest;
import com.teaclub.cloud.payment.dto.RefundApplyRequest;
import com.teaclub.cloud.payment.entity.PayNotifyLog;
import com.teaclub.cloud.payment.entity.PayPaymentOrder;
import com.teaclub.cloud.payment.entity.PayRefundOrder;
import com.teaclub.cloud.payment.gateway.*;
import com.teaclub.cloud.payment.mapper.PayNotifyLogMapper;
import com.teaclub.cloud.payment.mapper.PayPaymentOrderMapper;
import com.teaclub.cloud.payment.mapper.PayRefundOrderMapper;
import com.teaclub.cloud.payment.service.PaymentService;
import com.teaclub.cloud.payment.vo.PayCreateVO;
import com.teaclub.cloud.payment.vo.PaymentVO;
import com.teaclub.cloud.payment.vo.RefundVO;
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
    private final com.teaclub.cloud.messaging.outbox.OutboxService outboxService;
    private final OrderInternalClient orderClient;
    private final WalletInternalClient walletClient;

    // ==================== 创建支付 ====================

    /** 管理端（桌面收银台）默认渠道：需要二维码，所以是 Native。 */
    public static final String DEFAULT_ADMIN_CHANNEL = "WECHAT_NATIVE";

    /** 会员端（小程序）默认渠道：需要 invokeParams 调 wx.requestPayment。 */
    public static final String DEFAULT_MEMBER_CHANNEL = "WECHAT_JSAPI";

    /** 允许的渠道白名单，防止前端传入任意字符串写进库里。 */
    private static final Set<String> ALLOWED_CHANNELS =
            Set.of("WECHAT_NATIVE", "WECHAT_JSAPI", "MOCK_NATIVE", "MOCK");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayCreateVO createPayment(PayCreateRequest request) {
        // 管理端入口：桌面收银台无 openid，默认 Native 出二维码
        return doCreatePayment(request, DEFAULT_ADMIN_CHANNEL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayCreateVO createPaymentForMember(PayCreateRequest request) {
        // 会员端入口：小程序默认 JSAPI 出 invokeParams
        return doCreatePayment(request, DEFAULT_MEMBER_CHANNEL);
    }

    /**
     * 解析并校验支付渠道。
     *
     * <p>显式传入优先；不传则落到调用端默认值。
     * JSAPI 必须有 openid，否则在真实微信渠道下必然失败，这里提前拦掉。
     */
    private String resolveChannel(String requested, String defaultChannel) {
        String channel = StringUtils.hasText(requested) ? requested.trim() : defaultChannel;
        if (!ALLOWED_CHANNELS.contains(channel)) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "不支持的支付渠道：" + channel + "，可选 " + ALLOWED_CHANNELS);
        }
        return channel;
    }

    private PayCreateVO doCreatePayment(PayCreateRequest request, String defaultChannel) {
        LoginUser current = UserContext.require();

        // 统一解析渠道：显式传入优先，否则用调用端默认值；并做白名单校验
        String channel = resolveChannel(request.getChannel(), defaultChannel);
        request.setChannel(channel);

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
            Map<String, Object> order = fetchOrder(request.getBizNo());
            if (order == null) {
                throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
            }
            if (!"UNPAID".equals(str(ord(order, "status")))) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "订单状态为 " + str(ord(order, "status")) + "，不可发起支付");
            }
            StoreScope.from(current).assertAccessible(toLong(ord(order, "store_id")));
            amount = toLong(ord(order, "payable_amount"));
            storeId = toLong(ord(order, "store_id"));
            memberId = toLong(ord(order, "member_id"));
        } else {
            Map<String, Object> recharge = fetchRechargeOrder(request.getBizNo());
            if (recharge == null) {
                throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在");
            }
            if (!"PENDING".equals(str(recharge.get("status")))) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "充值订单状态为 " + str(recharge.get("status")) + "，不可发起支付");
            }
            StoreScope.from(current).assertAccessible(toLong(recharge.get("store_id")));
            amount = toLong(recharge.get("recharge_amount"));
            storeId = toLong(recharge.get("store_id"));
            memberId = toLong(recharge.get("member_id"));
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
            Map<String, Object> paying = new LinkedHashMap<>();
        paying.put("paymentNo", payment.getPaymentNo());
        paying.put("payChannel", request.getChannel());
        orderClient.markPaying(request.getBizNo(), paying);
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
        // 回写订单已支付（order-service 负责订单状态机）
        Map<String, Object> paid = new LinkedHashMap<>();
        paid.put("paymentNo", paymentNo);
        paid.put("payChannel", payment.getChannel());
        paid.put("paidAmount", payment.getAmount());
        orderClient.markPaid(payment.getBizNo(), paid);
        } else if ("RECHARGE".equals(payment.getBizType())) {
            // 充值支付成功 -> 由 wallet-service 完成入账（含充值单状态推进）
            // 幂等键 = 充值单号：重复通知不会重复入账
            Map<String, Object> rechargeBody = new LinkedHashMap<>();
            rechargeBody.put("rechargeNo", payment.getBizNo());
            rechargeBody.put("paymentNo", paymentNo);
            var rechargeResp = walletClient.rechargePaid(rechargeBody);
            if (rechargeResp == null || rechargeResp.getCode() != 0) {
                log.error("充值入账失败 rechargeNo={} resp={}", payment.getBizNo(), rechargeResp);
                throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                        "充值入账失败，钱包服务不可用或状态异常");
            }
            log.info("充值入账成功 rechargeNo={} paymentNo={}", payment.getBizNo(), paymentNo);
        } else {
            throw new BizException(ResultCode.BAD_REQUEST, "未知业务类型: " + payment.getBizType());
        }

        // 领域事件：支付成功（与业务数据同一本地事务，由 Outbox 异步投递）
        Map<String, Object> paidEvent = new LinkedHashMap<>();
        paidEvent.put("paymentNo", paymentNo);
        paidEvent.put("bizType", payment.getBizType());
        paidEvent.put("bizNo", payment.getBizNo());
        paidEvent.put("amount", payment.getAmount());
        paidEvent.put("channel", payment.getChannel());
        paidEvent.put("provider", payment.getProvider());
        paidEvent.put("transactionId", transactionId);
        outboxService.enqueue(
                com.teaclub.cloud.messaging.event.DomainEvents.PAYMENT_SUCCEEDED,
                payment.getBizNo(), paidEvent);

        log.info("支付成功处理完成 paymentNo={} bizType={} bizNo={}",
                paymentNo, payment.getBizType(), payment.getBizNo());
    }

    // ==================== 退款 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO applyRefund(RefundApplyRequest request) {
        LoginUser current = UserContext.require();

        Map<String, Object> order = fetchOrder(request.getOrderNo());
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        StoreScope.from(current).assertAccessible(toLong(ord(order, "store_id")));

        if (!"PAID".equals(str(ord(order, "status"))) && !"COMPLETED".equals(str(ord(order, "status")))
                && !"REFUNDING".equals(str(ord(order, "status")))) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单状态为 " + str(ord(order, "status")) + "，不可退款");
        }

        // 可退金额 = 实付 - 已占用退款额
        //
        // 【关键】已占用退款额取自 payment_db 本地的 pay_refund_order，
        // 而不是 order_db 的 refunded_amount —— 因为后者要等退款成功通知才更新，
        // 期间重复申请会绕过校验。退款单与校验在同一本地事务内，天然幂等。
        Long paidVal = toLong(ord(order, "paid_amount"));
        long paid = paidVal == null ? 0 : paidVal;
        Long reserved = refundMapper.sumReservedByBizNo(request.getOrderNo());
        long refundable = paid - (reserved == null ? 0 : reserved);
        if (refundable <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该订单已无可退金额（实付 " + paid + " 分，已占用退款 " + (reserved == null ? 0 : reserved) + " 分）");
        }
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
        String paymentRef = str(ord(order, "payment_no"));
        if (paymentRef == null || paymentRef.isBlank()) {
            paymentRef = ("BALANCE".equals(str(ord(order, "pay_channel"))) ? "BAL-" : "ORD-") + str(ord(order, "order_no"));
        }
        refund.setPaymentNo(paymentRef);
        refund.setBizType("ORDER");
        refund.setBizNo(str(ord(order, "order_no")));
        refund.setStoreId(toLong(ord(order, "store_id")));
        refund.setMemberId(toLong(ord(order, "member_id")));
        refund.setReason(request.getReason());
        refund.setApplyUserId(current.getUserId());
        refund.setRefundAmount(request.getRefundAmount());

        if ("BALANCE".equals(str(ord(order, "pay_channel")))) {
            refund.setChannel("BALANCE");
            // 按原扣款构成分摊：用减法保证两项之和精确等于退款额
            long origPrincipal = toLong(ord(order, "balance_principal_amount")) == null ? 0 : toLong(ord(order, "balance_principal_amount"));
            long origGift = toLong(ord(order, "balance_gift_amount")) == null ? 0 : toLong(ord(order, "balance_gift_amount"));
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

        // 执行退款（退款金额已在本地退款单表占用，重复申请会被上面的校验拦截）
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

    // ==================== 充值退卡（一键） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO refundRecharge(RechargeRefundRequest request) {
        LoginUser current = UserContext.require();
        String rechargeNo = request.getRechargeNo() == null ? null : request.getRechargeNo().trim();
        if (!StringUtils.hasText(rechargeNo)) {
            throw new BizException(ResultCode.PARAM_INVALID, "充值单号不能为空");
        }

        // 幂等：同一充值单已有进行中/成功的退款单 → 直接返回，不重复扣回、不重复退款。
        // FOR UPDATE + 间隙锁同时挡住「双击退卡」的并发建单。
        PayRefundOrder active = refundMapper.selectActiveByBizForUpdate("RECHARGE", rechargeNo);
        if (active != null) {
            log.info("充值退卡幂等命中 rechargeNo={} refundNo={} status={}",
                    rechargeNo, active.getRefundNo(), active.getStatus());
            return toRefundVO(active);
        }

        // 充值单权威数据在 wallet_db，只经内部 Feign 读取，金额一律服务端取
        Map<String, Object> recharge = fetchRechargeOrder(rechargeNo);
        if (recharge == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值单不存在: " + rechargeNo);
        }
        Long brandId = toLong(ord(recharge, "brand_id"));
        if (brandId != null && !brandId.equals(current.getBrandId())) {
            throw new BizException(ResultCode.FORBIDDEN, "充值单不属于当前品牌");
        }
        Long storeId = toLong(ord(recharge, "store_id"));
        StoreScope.from(current).assertAccessible(storeId);
        Long memberId = toLong(ord(recharge, "member_id"));

        if (!"PAID".equals(str(ord(recharge, "status")))) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "充值单状态为 " + str(ord(recharge, "status")) + "，仅已支付的充值单可退卡");
        }
        Long principalVal = toLong(ord(recharge, "recharge_amount"));
        Long giftVal = toLong(ord(recharge, "gift_amount"));
        long principal = principalVal == null ? 0 : principalVal;
        long gift = giftVal == null ? 0 : giftVal;
        if (principal <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值本金异常，无法退卡");
        }

        // 原支付单必须成功，现金按原路退回
        PayPaymentOrder payment = paymentMapper.selectByBizForUpdate("RECHARGE", rechargeNo);
        if (payment == null || !"SUCCESS".equals(payment.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值单没有成功的支付单，无法原路退款");
        }
        Long reserved = refundMapper.sumRefundedByPayment(payment.getPaymentNo());
        if (reserved != null && reserved > 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该充值支付单已发起过退款 " + reserved + " 分，不允许重复退卡");
        }

        String refundNo = BizNoGenerator.refundNo();
        String reason = StringUtils.hasText(request.getReason()) ? request.getReason().trim() : "顾客退卡";

        PayRefundOrder refund = new PayRefundOrder();
        refund.setBrandId(current.getBrandId());
        refund.setRefundNo(refundNo);
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setBizType("RECHARGE");
        refund.setBizNo(rechargeNo);
        refund.setStoreId(storeId);
        refund.setMemberId(memberId);
        refund.setChannel("WECHAT");
        // 退卡只把【实付本金】原路退回；赠送金不折现，只在钱包侧扣回。
        refund.setRefundAmount(principal);
        refund.setRefundCashAmount(principal);
        refund.setRefundPrincipalAmount(0L);
        refund.setRefundGiftAmount(0L);
        refund.setReason(reason + "；扣回实付 " + principal + " 分、赠送 " + gift + " 分");
        refund.setStatus("CREATED");
        refund.setApplyUserId(current.getUserId());
        refundMapper.insert(refund);

        // ① 先扣回钱包（独立事务，幂等键 refundNo）。
        //    顾客若已消费导致赠送余额不足，wallet 会拒绝 → 本地退款单回滚，
        //    绝不会出现「现金已退、储值还在」的品牌资金损失。
        try {
            var clawback = walletClient.rechargeRefundOut(brandId, memberId, storeId, refundNo,
                    principal, gift, reason, current.getUserId());
            if (clawback == null || clawback.getCode() != 0) {
                throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                        "钱包扣回失败：" + (clawback == null ? "无响应" : clawback.getMessage()));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("充值退卡扣回钱包失败 refundNo={} rechargeNo={}", refundNo, rechargeNo, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "钱包服务不可用，退卡未完成");
        }

        // ② 现金原路退回
        RefundResult result;
        try {
            result = provider.createRefund(RefundCreateCmd.builder()
                    .refundNo(refundNo)
                    .paymentNo(payment.getPaymentNo())
                    .totalAmount(payment.getAmount())
                    .refundAmount(principal)
                    .reason(reason)
                    .build());
        } catch (Exception e) {
            rollbackClawback(refund, principal, gift, storeId, current);
            markRefundFailed(refund, e.getMessage());
            throw new BizException(ResultCode.SYSTEM_ERROR, "退款渠道调用失败：" + e.getMessage());
        }
        if (result == null || !result.isSuccess()) {
            String failReason = result == null ? "渠道无响应" : result.getFailReason();
            rollbackClawback(refund, principal, gift, storeId, current);
            markRefundFailed(refund, failReason);
            throw new BizException(ResultCode.BAD_REQUEST, "退款渠道受理失败：" + failReason);
        }

        if ("SUCCESS".equals(result.getStatus())) {
            markRefundSuccess(refund, result.getRefundId());
            // 充值单状态推进失败则整体回滚，退款单不会停在 SUCCESS 而账没改
            settleRechargeRefund(rechargeNo, refundNo);
        } else {
            // PROCESSING：钱包已扣回，等退款成功通知由 handleRefundSuccess 收尾
            PayRefundOrder patch = new PayRefundOrder();
            patch.setId(refund.getId());
            patch.setStatus("PROCESSING");
            patch.setWxRefundId(result.getRefundId());
            refundMapper.updateById(patch);
            log.info("充值退卡退款受理中 refundNo={} rechargeNo={}", refundNo, rechargeNo);
        }
        return toRefundVO(refundMapper.selectByNoForUpdate(refundNo));
    }

    /** 渠道退款失败时，把已从钱包扣回的金额原样加回（幂等键：UNDO-退款单号）。 */
    private void rollbackClawback(PayRefundOrder refund, long principal, long gift,
                                  Long storeId, LoginUser current) {
        try {
            var resp = walletClient.adjust(refund.getBrandId(), refund.getMemberId(), storeId,
                    "UNDO-" + refund.getRefundNo(), principal, gift,
                    "退卡渠道受理失败，回补被扣回的钱包", current.getUserId());
            if (resp == null || resp.getCode() != 0) {
                log.error("退卡失败回补钱包失败 refundNo={} resp={}", refund.getRefundNo(), resp);
            }
        } catch (Exception e) {
            log.error("退卡失败回补钱包异常 refundNo={}", refund.getRefundNo(), e);
        }
    }

    /** 渠道即时成功 / 退款通知到达时，把充值单推进为 REFUNDED。 */
    private void settleRechargeRefund(String rechargeNo, String refundNo) {
        try {
            var resp = walletClient.markRechargeRefunded(rechargeNo, refundNo);
            if (resp == null || resp.getCode() != 0) {
                throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                        "充值单状态推进失败：" + (resp == null ? "无响应" : resp.getMessage()));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("充值单标记已退款异常 rechargeNo={} refundNo={}", rechargeNo, refundNo, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "钱包服务不可用，充值单状态未更新");
        }
    }

    private void executeRefund(PayRefundOrder refund, Map<String, Object> order) {
        boolean fullyRefunded = (toLong(ord(order, "refunded_amount")) == null ? 0 : toLong(ord(order, "refunded_amount")))
                + refund.getRefundAmount() >= toLong(ord(order, "paid_amount"));

        try {
            if ("BALANCE".equals(refund.getChannel())) {
                // 余额退款：回冲钱包，按原资金构成
                Map<String, Object> refundCmd = new LinkedHashMap<>();
                refundCmd.put("brandId", refund.getBrandId());
                refundCmd.put("memberId", refund.getMemberId());
                refundCmd.put("storeId", refund.getStoreId());
                refundCmd.put("refundNo", refund.getRefundNo());
                refundCmd.put("refundPrincipalAmount", refund.getRefundPrincipalAmount());
                refundCmd.put("refundGiftAmount", refund.getRefundGiftAmount());
                refundCmd.put("remark", refund.getReason());
                refundCmd.put("operatorId", refund.getApplyUserId());
                refundCmd.put("operatorType", "STAFF");
                var refundResp = walletClient.refundIn(refundCmd);
                if (refundResp == null || refundResp.getCode() != 0) {
                    throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                            "钱包服务回冲退款失败");
                }

                markRefundSuccess(refund, null);
                Map<String, Object> refundApply = new LinkedHashMap<>();
                refundApply.put("refundAmount", refund.getRefundAmount());
                refundApply.put("fullyRefunded", fullyRefunded);
                orderClient.applyRefund(str(ord(order, "order_no")), refundApply);
            } else {
                // 微信退款：调渠道
                RefundResult result = provider.createRefund(RefundCreateCmd.builder()
                        .refundNo(refund.getRefundNo())
                        .paymentNo(refund.getPaymentNo())
                        .totalAmount(toLong(ord(order, "paid_amount")))
                        .refundAmount(refund.getRefundAmount())
                        .reason(refund.getReason())
                        .build());

                if (result.isSuccess()) {
                    if ("SUCCESS".equals(result.getStatus())) {
                        markRefundSuccess(refund, result.getRefundId());
                        Map<String, Object> refundApply = new LinkedHashMap<>();
                        refundApply.put("refundAmount", refund.getRefundAmount());
                        refundApply.put("fullyRefunded", fullyRefunded);
                        orderClient.applyRefund(str(ord(order, "order_no")), refundApply);
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

        // 领域事件：退款成功
        Map<String, Object> refundEvent = new LinkedHashMap<>();
        refundEvent.put("refundNo", refund.getRefundNo());
        refundEvent.put("paymentNo", refund.getPaymentNo());
        refundEvent.put("bizNo", refund.getBizNo());
        refundEvent.put("refundAmount", refund.getRefundAmount());
        refundEvent.put("refundPrincipalAmount", refund.getRefundPrincipalAmount());
        refundEvent.put("refundGiftAmount", refund.getRefundGiftAmount());
        refundEvent.put("channel", refund.getChannel());
        outboxService.enqueue(
                com.teaclub.cloud.messaging.event.DomainEvents.REFUND_SUCCEEDED,
                refund.getRefundNo(), refundEvent);
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
            Map<String, Object> order = fetchOrder(refund.getBizNo());
            if (order != null) {
                boolean fully = (toLong(ord(order, "refunded_amount")) == null ? 0 : toLong(ord(order, "refunded_amount")))
                        + refund.getRefundAmount() >= toLong(ord(order, "paid_amount"));
                Map<String, Object> refundApply = new LinkedHashMap<>();
                refundApply.put("refundAmount", refund.getRefundAmount());
                refundApply.put("fullyRefunded", fully);
                orderClient.applyRefund(str(ord(order, "order_no")), refundApply);
            }
        } else if ("RECHARGE".equals(refund.getBizType())) {
            // 钱包的扣回在申请退卡时已完成（幂等），这里只推进充值单状态
            settleRechargeRefund(refund.getBizNo(), refund.getRefundNo());
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

    // ==================== 跨服务调用与字段读取辅助 ====================

    private static Object ord(Map<String, Object> order, String key) {
        return order == null ? null : order.get(key);
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Map<String, Object> fetchOrder(String orderNo) {
        try {
            var resp = orderClient.order(orderNo);
            return resp == null || resp.getCode() != 0 ? null : resp.getData();
        } catch (Exception e) {
            log.error("查询订单失败: orderNo={}", orderNo, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "订单服务不可用");
        }
    }

    private Map<String, Object> fetchRechargeOrder(String rechargeNo) {
        try {
            var resp = walletClient.rechargeOrder(rechargeNo);
            return resp == null || resp.getCode() != 0 ? null : resp.getData();
        } catch (Exception e) {
            log.error("查询充值订单失败: rechargeNo={}", rechargeNo, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "钱包服务不可用");
        }
    }
}
