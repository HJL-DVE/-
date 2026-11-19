package com.teaclub.cloud.member.consumer;

import com.rabbitmq.client.Channel;
import com.teaclub.cloud.member.service.MemberConsumeStatsUpdater;
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
 * 订单已支付事件消费者：累加会员消费档案。
 *
 * <p>这是本项目第一个真实的业务事件消费者。
 * 它取代了原先 order-service 里的<b>同步 Feign 调用</b>，
 * 使「下单支付」与「会员档案累加」解耦：
 * 订单服务只负责发事实，会员服务自己决定怎么用。
 *
 * <p>可靠性由 {@link AbstractEventConsumer} 统一保证：
 * 手动 ACK、失败 nack 重试、业务异常直接进死信、TraceId 透传、Redis 预筛。
 * 真正的幂等由 {@code MemberConsumeStatsUpdater} 的数据库 Inbox 保证。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.messaging.order-paid-consumer-enabled",
        havingValue = "true", matchIfMissing = true)
public class OrderPaidConsumer extends AbstractEventConsumer {

    private final MemberConsumeStatsUpdater updater;

    @RabbitListener(queues = MqConstants.Q_MEMBER_ORDER_PAID)
    public void onOrderPaidMessage(Message message, Channel channel) throws Exception {
        super.onMessage(message, channel);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handle(EventMessage<?> event) {
        if (!DomainEvents.ORDER_PAID.equals(event.getEventType())) {
            log.warn("[ORDER_PAID] 队列收到非预期事件类型 {}，直接确认", event.getEventType());
            return;
        }
        Object payload = event.getPayload();
        if (!(payload instanceof Map)) {
            log.error("[ORDER_PAID] 事件载荷格式异常，丢弃 eventId={}", event.getEventId());
            return;
        }
        boolean applied = updater.applyOrderPaid(
                event.getEventId(), event.getEventType(), event.getTraceId(),
                (Map<String, Object>) payload);
        if (!applied) {
            log.info("[ORDER_PAID] 幂等命中，未重复累加 eventId={}", event.getEventId());
        }
    }
}
