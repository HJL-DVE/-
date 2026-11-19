package com.teaclub.cloud.messaging.outbox;

import com.teaclub.cloud.messaging.constant.MqConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Transactional Outbox Relay。
 *
 * <p>定时扫描 sys_outbox_message 中未投递的消息，发送到 RabbitMQ。
 * 投递成功后标记 SENT；失败则标记 FAILED 并累加 attempts，由后续扫描重试。
 *
 * <p><b>至少一次投递</b>：RabbitMQ 投递成功后才标记 SENT，
 * 若进程在投递后标记前崩溃，重启后会重复投递 —— 消费端必须通过 Inbox 幂等去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxStore store;
    private final RabbitTemplate rabbitTemplate;

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelayString = "${teaclub.outbox.poll-ms:1000}")
    public void relay() {
        List<OutboxMessage> pending = store.findPending(BATCH_SIZE);
        for (OutboxMessage msg : pending) {
            try {
                rabbitTemplate.convertAndSend(
                        MqConstants.BUSINESS_EXCHANGE,
                        msg.getRoutingKey(),
                        msg.getPayload(),
                        m -> {
                            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            m.getMessageProperties().setHeader(MqConstants.HEADER_EVENT_ID, msg.getEventId());
                            m.getMessageProperties().setHeader(MqConstants.HEADER_EVENT_TYPE, msg.getEventType());
                            if (msg.getTraceId() != null) {
                                m.getMessageProperties().setHeader(MqConstants.HEADER_TRACE_ID, msg.getTraceId());
                            }
                            return m;
                        });
                store.markSent(msg.getEventId());
                log.debug("Outbox 投递成功: eventId={} routingKey={}", msg.getEventId(), msg.getRoutingKey());
            } catch (Exception e) {
                log.error("Outbox 投递失败: eventId={} error={}", msg.getEventId(), e.getMessage());
                store.markFailed(msg.getEventId(), e.getMessage());
            }
        }
    }
}
