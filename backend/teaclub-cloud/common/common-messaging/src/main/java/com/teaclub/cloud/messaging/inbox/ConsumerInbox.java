package com.teaclub.cloud.messaging.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consumer Inbox —— 基于数据库唯一索引的持久化消费幂等。

 * <p>表：sys_inbox_message(event_id VARCHAR(64) PRIMARY KEY, ...)
 * 消费端在处理业务<b>之前</b>尝试插入 event_id：
 * <ul>
 *   <li>插入成功 → 首次消费，继续处理</li>
 *   <li>主键冲突 → 重复投递，直接丢弃</li>
 * </ul>
 *
 * <p><b>与 Redis Inbox 的关系</b>：
 * Redis 守卫是第一道高速筛子；数据库 Inbox 是最终防线，
 * 两者都不替代资金业务本身的唯一索引幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerInbox {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return true 表示首次消费（应继续处理）；false 表示重复（应丢弃）
     */
    public boolean tryClaim(String eventId, String eventType, String traceId) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("[Inbox] 消息缺少 eventId，拒绝处理（无法幂等）");
            return false;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO sys_inbox_message (event_id, event_type, trace_id, received_at, status) "
                            + "VALUES (?,?,?,?,'PROCESSED')",
                    eventId, eventType, traceId, LocalDateTime.now());
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("[Inbox] 重复消息已忽略 eventId={}", eventId);
            return false;
        }
    }
}
