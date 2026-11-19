package com.teaclub.cloud.messaging.publisher;

import com.teaclub.cloud.core.api.TraceIdHolder;
import com.teaclub.cloud.messaging.constant.MqConstants;
import com.teaclub.cloud.messaging.model.EventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 统一事件发布器。
 *
 * <p>所有跨服务消息必须通过这里的 {@link #publish} 发出，
 * 以保证事件信封、消息头、持久化策略一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布领域事件。
     *
     * @param routingKey 路由键，见 {@link MqConstants}
     * @param payload    业务载荷
     */
    public <T> void publish(String routingKey, T payload) {
        String traceId = TraceIdHolder.get();
        EventMessage<T> event = EventMessage.of(routingKey, traceId, payload);

        rabbitTemplate.convertAndSend(
                MqConstants.BUSINESS_EXCHANGE,
                routingKey,
                event,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setHeader(MqConstants.HEADER_EVENT_ID, event.getEventId());
                    message.getMessageProperties().setHeader(MqConstants.HEADER_EVENT_TYPE, event.getEventType());
                    message.getMessageProperties().setHeader(MqConstants.HEADER_OCCURRED_AT,
                            event.getOccurredAt().toString());
                    if (traceId != null) {
                        message.getMessageProperties().setHeader(MqConstants.HEADER_TRACE_ID, traceId);
                    }
                    return message;
                });

        log.info("[MQ] 事件已发布 routingKey={} eventId={} traceId={}",
                routingKey, event.getEventId(), traceId);
    }
}
