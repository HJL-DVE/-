package com.teaclub.cloud.payment.service;

import com.teaclub.cloud.payment.entity.PayPaymentOrder;
import com.teaclub.cloud.payment.gateway.PaymentProvider;
import com.teaclub.cloud.payment.mapper.PayPaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时关单任务。
 *
 * <p>解决「超时支付单永久停留在 PAYING」的问题：
 * 超过配置时限仍为 CREATED / PAYING 的支付单，调用渠道关单并置为 CLOSED。
 *
 * <p><b>为什么不会误关已支付的单</b>：
 * <ul>
 *   <li>只扫 CREATED / PAYING，SUCCESS 是终态不会被选中</li>
 *   <li>关单前再次加行锁复核状态，避免与「成功通知」并发冲突</li>
 *   <li>关单只改支付单，不动订单与钱包 —— 订单仍是 UNPAID，用户可重新发起支付</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutCloser {

    private final PayPaymentOrderMapper paymentMapper;
    private final PaymentProvider provider;

    @Value("${teaclub.payment.timeout-minutes:30}")
    private int timeoutMinutes;

    @Value("${teaclub.payment.timeout-close-enabled:true}")
    private boolean enabled;

    private static final int BATCH = 50;

    @Scheduled(fixedDelayString = "${teaclub.payment.timeout-scan-ms:60000}")
    public void closeTimeoutPayments() {
        if (!enabled) {
            return;
        }
        List<PayPaymentOrder> stale = paymentMapper.findTimeout(timeoutMinutes, BATCH);
        for (PayPaymentOrder p : stale) {
            try {
                closeOne(p.getPaymentNo());
            } catch (Exception e) {
                log.error("关闭超时支付单失败 paymentNo={}", p.getPaymentNo(), e);
            }
        }
    }

    /**
     * 关闭单个支付单。加行锁复核状态，避免与成功通知竞争。
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeOne(String paymentNo) {
        PayPaymentOrder payment = paymentMapper.selectByNoForUpdate(paymentNo);
        if (payment == null) {
            return;
        }
        if (!"CREATED".equals(payment.getStatus()) && !"PAYING".equals(payment.getStatus())) {
            // 期间已被成功通知推进，绝不能关单
            log.info("支付单已推进为 {}，跳过关单 paymentNo={}", payment.getStatus(), paymentNo);
            return;
        }

        // 先请渠道关单（失败不阻断本地关单，避免永远关不掉）
        try {
            provider.closeOrder(paymentNo);
        } catch (Exception e) {
            log.warn("渠道关单失败（继续本地关单） paymentNo={} err={}", paymentNo, e.getMessage());
        }

        PayPaymentOrder patch = new PayPaymentOrder();
        patch.setId(payment.getId());
        patch.setStatus("CLOSED");
        patch.setFailReason("支付超时自动关闭（>" + timeoutMinutes + " 分钟）");
        paymentMapper.updateById(patch);
        log.warn("支付单超时已关闭 paymentNo={} bizType={} bizNo={} 金额={}",
                paymentNo, payment.getBizType(), payment.getBizNo(), payment.getAmount());
    }
}
