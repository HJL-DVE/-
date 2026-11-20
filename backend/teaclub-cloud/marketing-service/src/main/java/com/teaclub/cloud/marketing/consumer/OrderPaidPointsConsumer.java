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
 * 订单已支付 → 发放消费积分。
 *
 * <p><b>不重复发放</b>由两层保证：
 * <ol>
 *   <li>{@link AbstractEventConsumer} 的 Redis 预筛（快，但可能失效）</li>
 *   <li>{@code mkt_points_transaction} 的 {@code uk_points_biz} 唯一索引（最终防线）</li>
 * </ol>
 * 第二层是关键：Redis 重启、消息重复投递都不会导致多发积分。
 *
 * <p><b>为什么单独一个队列</b>：和 member-service 的 ORDER_PAID 消费者并列消费同一事件，
 * 各自用独立队列（topic 交换机会把同一消息投给所有匹配队列），
 * 互不影响、互不阻塞。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.marketing.order-points-consumer-enabled",
        havingValue = "true", matchIfMissing = true)
public class OrderPaidPointsConsumer extends AbstractEventConsumer {

    private final PointsService pointsService;

    @RabbitListener(queues = MqConstants.Q_MARKETING_ORDER_PAID)
    public void onMessage(Message message, Channel channel) throws Exception {
        super.onMessage(message, channel);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handle(EventMessage<?> event) {
        if (!DomainEvents.ORDER_PAID.equals(event.getEventType())) {
            log.warn("[POINTS-ORDER] 队列收到非预期事件 {}，直接确认", event.getEventType());
            return;
        }
        Object payload = event.getPayload();
        if (!(payload instanceof Map)) {
            log.error("[POINTS-ORDER] 载荷格式异常，丢弃 eventId={}", event.getEventId());
            return;
        }
        Map<String, Object> m = (Map<String, Object>) payload;
        Long brandId = toLong(m.get("brandId"));
        Long memberId = toLong(m.get("memberId"));
        Long storeId = toLong(m.get("storeId"));
        Long paidAmount = toLong(m.get("paidAmount"));
        String orderNo = str(m.get("orderNo"));
        if (memberId == null || orderNo == null) {
            log.debug("[POINTS-ORDER] 散客订单，不发积分 orderNo={}", orderNo);
            return;
        }
        boolean applied = pointsService.earnByOrder(brandId == null ? 1L : brandId, memberId,
                storeId, orderNo, paidAmount, event.getTraceId());
        if (!applied) {
            log.info("[POINTS-ORDER] 未发放（已发过或金额不足）orderNo={}", orderNo);
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
