package com.teaclub.cloud.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaclub.cloud.core.api.TraceIdHolder;
import com.teaclub.cloud.messaging.event.DomainEvents;
import com.teaclub.cloud.messaging.model.EventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox 事件写入服务。
 *
 * <p>在业务服务的事务方法内调用 {@link #enqueue(String, String, Object)}，
 * 「业务数据变更 + outbox 记录」由同一个本地事务保证原子性。
 *
 * <p>事件投递由 {@link OutboxRelay} 异步完成，业务方法不需要同步等待 RabbitMQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxStore store;
    private final ObjectMapper objectMapper;

    /**
     * 在业务事务内登记一个领域事件。
     *
     * @param eventType 事件类型（见 DomainEvents）
     * @param bizNo     业务单号（可追溯）
     * @param payload   事件载荷
     * @return eventId 全局唯一事件 ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueue(String eventType, String bizNo, Object payload) {
        String eventId = UUID.randomUUID().toString().replace("-", "");
        String routingKey = DomainEvents.routingKey(eventType);
        String traceId = TraceIdHolder.get();

        EventMessage<Object> event = EventMessage.builder()
                .eventId(eventId)
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .version(1)
                .traceId(traceId)
                .payload(payload)
                .build();

        String json;
        try {
            json = objectMapper.writeValueAsString(OutboxEnvelope.of(event, routingKey, bizNo));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("事件序列化失败: " + eventType, e);
        }

        OutboxMessage record = OutboxMessage.builder()
                .eventId(eventId)
                .eventType(eventType)
                .bizNo(bizNo)
                .routingKey(routingKey)
                .payload(json)
                .version(1)
                .traceId(traceId)
                .occurredAt(event.getOccurredAt())
                .status("PENDING")
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        store.save(record);
        log.info("Outbox 登记事件: eventId={} type={} bizNo={}", eventId, eventType, bizNo);
        return eventId;
    }

    /** Outbox 内部信封（含路由键与业务单号）。 */
    public static class OutboxEnvelope {
        public EventMessage<?> event;
        public String routingKey;
        public String bizNo;

        public static OutboxEnvelope of(EventMessage<?> event, String routingKey, String bizNo) {
            OutboxEnvelope e = new OutboxEnvelope();
            e.event = event;
            e.routingKey = routingKey;
            e.bizNo = bizNo;
            return e;
        }
    }
}
