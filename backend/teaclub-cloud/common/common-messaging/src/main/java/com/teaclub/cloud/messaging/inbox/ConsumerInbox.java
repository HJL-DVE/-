package com.teaclub.cloud.messaging.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consumer Inbox —— 基于数据库唯一索引的持久化消费幂等。
 *
 * <pre>
 *   INSERT INTO sys_inbox_message(event_id, ...) VALUES (?)
 *     成功 → 首次消费，继续处理
 *     主键冲突 → 已消费过，跳过
 * </pre>
 *
 * <h3>与业务的原子性</h3>
 * 认领记录 <b>必须与业务写在同一事务</b>，由调用方保证
 * （业务方法标 {@code @Transactional}）。
 *
 * <p>如果业务失败回滚，认领记录也一起回滚 —— 重放时可以重新认领并重试。
 *
 * <h3>历史缺陷（已修复，勿回退）</h3>
 * 曾出现「认领成功但业务失败」的脏数据：业务事务回滚后认领记录残留，
 * 重放时被判定为"已处理"直接跳过，消息永远处理不了且无报错。
 * 现在的 {@link #release(String)} 提供显式回滚能力，
 * 消费者在失败分支调用它清理认领（应对调用方没开事务的情况）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerInbox {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 尝试认领该事件。
     *
     * @return true 首次消费（应继续处理）；false 已消费过
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

    /**
     * 释放认领（业务失败时调用）。
     *
     * <p>业务与认领在同一事务时不需要调用：回滚会自动撤销。
     * 但为覆盖「调用方未开事务」或「认领先提交」的情况，
     * 消费者在失败分支显式删除认领，保证重放能重新认领。
     *
     * <p>幂等：记录不存在时静默返回。
     */
    public void release(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            int n = jdbcTemplate.update("DELETE FROM sys_inbox_message WHERE event_id = ?", eventId);
            if (n > 0) {
                log.warn("[Inbox] 已释放认领，允许重放重试 eventId={}", eventId);
            }
        } catch (Exception e) {
            log.error("[Inbox] 释放认领失败 eventId={} err={}", eventId, e.getMessage());
        }
    }
}
