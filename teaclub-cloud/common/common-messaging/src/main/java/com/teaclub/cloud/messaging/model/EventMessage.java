package com.teaclub.cloud.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一领域事件信封。
 *
 * <p>所有跨服务消息都必须包在此结构中，保证：
 * <ul>
 *   <li>{@code eventId} 全局唯一 —— 消费端以此为幂等键</li>
 *   <li>{@code occurredAt} 记录事实发生时间</li>
 *   <li>{@code version} 支持后续契约演进</li>
 *   <li>{@code traceId} 贯穿全链路</li>
 * </ul>
 *
 * <p><b>事件只表达"已发生的事实"，不承载指令。</b>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage<T> implements Serializable {

    /** 全局唯一事件 ID（消费端幂等键） */
    private String eventId;

    /** 事件类型，如 payment.order.success */
    private String eventType;

    /** 事实发生时间 */
    private LocalDateTime occurredAt;

    /** 事件契约版本 */
    private Integer version;

    /** 链路追踪 ID */
    private String traceId;

    /** 事件载荷 */
    private T payload;

    public static <T> EventMessage<T> of(String eventType, String traceId, T payload) {
        return EventMessage.<T>builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .version(1)
                .traceId(traceId)
                .payload(payload)
                .build();
    }
}
