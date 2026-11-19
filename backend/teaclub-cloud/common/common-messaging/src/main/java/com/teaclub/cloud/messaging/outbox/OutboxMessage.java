package com.teaclub.cloud.messaging.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Transactional Outbox 消息记录。
 *
 * <p><b>核心机制</b>：业务数据在同一个本地事务内写入业务表 + outbox 表，
 * 由独立的 outbox relay 异步扫描未发送消息并投递到 RabbitMQ。
 * 这样避免了「跨服务一致性」难题 —— 数据库事务保证业务与事件同时成功或同时失败。
 *
 * <p>表结构：`sys_outbox_message`（各服务库均有此表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {

    /** 事件唯一 ID（同时是消费端幂等键） */
    private String eventId;

    /** 事件类型（见 DomainEvents） */
    private String eventType;

    /** 业务单号（可追溯） */
    private String bizNo;

    /** 路由键 */
    private String routingKey;

    /** 事件载荷 JSON */
    private String payload;

    /** 契约版本 */
    private Integer version;

    /** 链路追踪 ID */
    private String traceId;

    /** 事实发生时间 */
    private LocalDateTime occurredAt;

    /** 投递状态：PENDING / SENT / FAILED */
    private String status;

    /** 已尝试次数 */
    private Integer attempts;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后错误 */
    private String lastError;
}
