package com.teaclub.cloud.order.saga;

import com.teaclub.cloud.order.client.WalletInternalClient;
import com.teaclub.cloud.order.entity.OmsOrder;
import com.teaclub.cloud.order.entity.OmsPaymentSaga;
import com.teaclub.cloud.order.enums.OrderStatus;
import com.teaclub.cloud.order.mapper.OmsOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 余额支付 Saga 定时补偿任务。
 *
 * <p>扫描滞留的 Saga（扣款成功但订单未更新且进程已退出等场景），
 * 对订单仍未支付的记录调用钱包回冲，把资金退回会员。
 *
 * <p>这是「不允许出现钱已经扣了但订单永久待支付」的最后一道防线：
 * 即使进程在扣款后立即崩溃（来不及执行 catch 补偿），本任务也能兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSagaCompensator {

    private final PaymentSagaService sagaService;
    private final OmsOrderMapper orderMapper;
    private final WalletInternalClient walletClient;

    /** 超过 60 秒仍未推进的 Saga 视为滞留。 */
    private static final int STALE_SECONDS = 60;

    @Scheduled(fixedDelayString = "${teaclub.saga.compensate-ms:30000}")
    public void compensate() {
        List<OmsPaymentSaga> stale = sagaService.findStale(STALE_SECONDS);
        if (stale.isEmpty()) {
            return;
        }
        log.warn("[Saga补偿] 发现 {} 条滞留记录", stale.size());
        for (OmsPaymentSaga saga : stale) {
            try {
                OmsOrder order = orderMapper.selectByNo(saga.getOrderNo());
                if (order == null) {
                    sagaService.markFailed(saga.getId(), "订单不存在");
                    continue;
                }
                if (OrderStatus.PAID.name().equals(order.getStatus())) {
                    // 订单其实已支付成功，只需把 Saga 标记完成
                    sagaService.markCompleted(saga.getId());
                    log.info("[Saga补偿] 订单已支付，标记完成 orderNo={}", saga.getOrderNo());
                    continue;
                }

                // 订单未支付 -> 回冲已扣资金
                Map<String, Object> comp = new LinkedHashMap<>();
                comp.put("brandId", order.getBrandId());
                comp.put("memberId", order.getMemberId());
                comp.put("storeId", order.getStoreId());
                comp.put("refundNo", "COMP-" + saga.getOrderNo());
                comp.put("refundPrincipalAmount",
                        saga.getPrincipalAmount() == null ? 0L : saga.getPrincipalAmount());
                comp.put("refundGiftAmount",
                        saga.getGiftAmount() == null ? 0L : saga.getGiftAmount());
                comp.put("remark", "Saga 超时自动补偿");
                comp.put("operatorType", "SYSTEM");
                var resp = walletClient.refundIn(comp);
                if (resp != null && resp.getCode() == 0) {
                    sagaService.markCompensated(saga.getId(), "定时补偿已回冲");
                    log.warn("[Saga补偿] 回冲成功 orderNo={} 实付={} 赠送={}",
                            saga.getOrderNo(), saga.getPrincipalAmount(), saga.getGiftAmount());
                } else {
                    sagaService.markFailed(saga.getId(), "定时补偿回冲失败");
                    log.error("[Saga补偿] 回冲失败 orderNo={} resp={}", saga.getOrderNo(), resp);
                }
            } catch (Exception e) {
                log.error("[Saga补偿] 处理异常 orderNo={}", saga.getOrderNo(), e);
                sagaService.markFailed(saga.getId(), "补偿异常: " + e.getMessage());
            }
        }
    }
}
