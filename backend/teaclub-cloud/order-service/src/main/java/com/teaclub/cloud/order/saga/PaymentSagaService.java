package com.teaclub.cloud.order.saga;

import com.teaclub.cloud.order.entity.OmsPaymentSaga;
import com.teaclub.cloud.order.mapper.OmsPaymentSagaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 余额支付 Saga 状态机服务。
 *
 * <p><b>为什么用 REQUIRES_NEW</b>：Saga 记录必须在业务事务回滚后依然存在，
 * 否则「扣款成功但订单更新失败」时记录被回滚，补偿任务无从发现孤儿扣款。
 * 独立事务写入是补偿任务能够兜底的前提。
 *
 * <p>状态流转：
 * <pre>
 * DEDUCTING (RUNNING)   扣款前
 * DEDUCTED  (RUNNING)   钱包已扣款，订单尚未更新
 * ORDER_PAID(COMPLETED) 订单已更新，Saga 完成
 * COMPENSATED           已回冲，资金已退回
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSagaService {

    private final OmsPaymentSagaMapper sagaMapper;

    /** 扣款前登记（独立事务，外层回滚不影响）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OmsPaymentSaga begin(String orderNo, long payable) {
        OmsPaymentSaga existed = sagaMapper.selectByOrderNo(orderNo);
        if (existed != null) {
            log.info("[Saga] 复用已有记录 orderNo={} step={} status={}",
                    orderNo, existed.getStep(), existed.getStatus());
            return existed;
        }
        OmsPaymentSaga saga = new OmsPaymentSaga();
        saga.setSagaId(UUID.randomUUID().toString().replace("-", ""));
        saga.setOrderNo(orderNo);
        saga.setStep("DEDUCTING");
        saga.setStatus("RUNNING");
        saga.setPrincipalAmount(payable);
        saga.setGiftAmount(0L);
        sagaMapper.insert(saga);
        log.info("[Saga] begin orderNo={} sagaId={} payable={}", orderNo, saga.getSagaId(), payable);
        return saga;
    }

    /** 钱包已扣款（记录实际扣款构成）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeducted(Long id, long principal, long gift) {
        OmsPaymentSaga patch = new OmsPaymentSaga();
        patch.setId(id);
        patch.setStep("DEDUCTED");
        patch.setPrincipalAmount(principal);
        patch.setGiftAmount(gift);
        sagaMapper.updateById(patch);
    }

    /** 订单已更新，Saga 完成。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long id) {
        OmsPaymentSaga patch = new OmsPaymentSaga();
        patch.setId(id);
        patch.setStep("ORDER_PAID");
        patch.setStatus("COMPLETED");
        sagaMapper.updateById(patch);
    }

    /** 已回冲补偿。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensated(Long id, String remark) {
        OmsPaymentSaga patch = new OmsPaymentSaga();
        patch.setId(id);
        patch.setStep("COMPENSATED");
        patch.setStatus("COMPENSATED");
        patch.setRemark(truncate(remark));
        sagaMapper.updateById(patch);
    }

    /** 补偿失败，标记待人工介入。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String remark) {
        OmsPaymentSaga patch = new OmsPaymentSaga();
        patch.setId(id);
        patch.setStatus("FAILED");
        patch.setRemark(truncate(remark));
        sagaMapper.updateById(patch);
    }

    /** 扫描滞留 Saga（供补偿任务）。 */
    public List<OmsPaymentSaga> findStale(int seconds) {
        return sagaMapper.findStale(seconds);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 250 ? s.substring(0, 250) : s;
    }
}
