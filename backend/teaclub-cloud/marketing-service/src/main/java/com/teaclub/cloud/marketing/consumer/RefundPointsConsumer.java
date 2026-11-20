package com.teaclub.cloud.marketing.consumer;

import com.rabbitmq.client.Channel;
import com.teaclub.cloud.marketing.service.PointsService;
import com.teaclub.cloud.messaging.constant.MqConstants;
import com.teaclub.cloud.messaging.consumer.AbstractEventConsumer;
import com.teaclub.cloud.messaging.event.DomainEvents;
import com.teaclub.cloud.messaging.model.EventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 退款成功 → 积分冲正。
 *
 * <p><b>冲正严格按原始记录</b>：不重新按退款金额算积分，
 * 而是查出当初 {@code ORDER_EARN} 那条流水，原额扣回。
 * 原因：积分规则可能中途改过（比如从"1 元 1 分"改成"2 元 1 分"），
 * 按新规则重算会导致一进一出金额不一致，账对不平。
 *
 * <p>冲正流水用 {@code ORDER_REFUND_REVERSE + 订单号} 做唯一键，
 * 重复退款消息不会重复冲正。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.marketing.refund-points-consumer-enabled",
        havingValue = "true", matchIfMissing = true)
public class RefundPointsConsumer extends AbstractEventConsumer {

    private final PointsService pointsService;

    @RabbitListener(queues = MqConstants.Q_MARKETING_REFUND_SUCCESS)
    public void onMessage(Message message, Channel channel) throws Exception {
        super.onMessage(message, channel);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handle(EventMessage<?> event) {
        if (!DomainEvents.REFUND_SUCCEEDED.equals(event.getEventType())) {
            log.warn("[POINTS-REFUND] 队列收到非预期事件 {}，直接确认", event.getEventType());
            return;
        }
        Object payload = event.getPayload();
        if (!(payload instanceof Map)) {
            log.error("[POINTS-REFUND] 载荷格式异常，丢弃 eventId={}", event.getEventId());
            return;
        }
        Map<String, Object> m = (Map<String, Object>) payload;
        String orderNo = str(m.get("bizNo"));
        Long refundAmount = toLong(m.get("refundAmount"));
        Long memberId = toLong(m.get("memberId"));
        Long brandId = toLong(m.get("brandId"));

        if (orderNo == null) {
            log.warn("[POINTS-REFUND] 事件缺少 bizNo，无法定位订单 eventId={}", event.getEventId());
            return;
        }
        // 会员 ID 优先取事件里的；缺失时由冲正逻辑按订单流水反查
        boolean reversed = pointsService.reverseByRefund(
                brandId == null ? 1L : brandId, memberId, orderNo, refundAmount, event.getTraceId());
        if (!reversed) {
            log.info("[POINTS-REFUND] 未冲正（无原记录/已冲正/余额为0）orderNo={}", orderNo);
        }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
