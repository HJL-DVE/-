package com.teaclub.cloud.messaging.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于 JDBC 的 Outbox 存储。
 *
 * <p>各服务库需建表：
 * <pre>
 * CREATE TABLE sys_outbox_message (
 *   event_id    VARCHAR(64)  NOT NULL PRIMARY KEY,
 *   event_type  VARCHAR(64)  NOT NULL,
 *   biz_no      VARCHAR(64)  NULL,
 *   routing_key VARCHAR(128) NOT NULL,
 *   payload     TEXT         NOT NULL,
 *   version     INT          NOT NULL DEFAULT 1,
 *   trace_id    VARCHAR(64)  NULL,
 *   occurred_at DATETIME(3)  NOT NULL,
 *   status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
 *   attempts    INT          NOT NULL DEFAULT 0,
 *   created_at  DATETIME(3)  NOT NULL,
 *   last_error  VARCHAR(512) NULL,
 *   KEY idx_status_created (status, created_at)
 * );
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<OutboxMessage> MAPPER = (rs, n) -> OutboxMessage.builder()
            .eventId(rs.getString("event_id"))
            .eventType(rs.getString("event_type"))
            .bizNo(rs.getString("biz_no"))
            .routingKey(rs.getString("routing_key"))
            .payload(rs.getString("payload"))
            .version(rs.getInt("version"))
            .traceId(rs.getString("trace_id"))
            .occurredAt(rs.getTimestamp("occurred_at").toLocalDateTime())
            .status(rs.getString("status"))
            .attempts(rs.getInt("attempts"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .lastError(rs.getString("last_error"))
            .build();

    @Override
    public void save(OutboxMessage m) {
        jdbcTemplate.update(
                "INSERT INTO sys_outbox_message "
                        + "(event_id, event_type, biz_no, routing_key, payload, version, "
                        + " trace_id, occurred_at, status, attempts, created_at, last_error) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                m.getEventId(), m.getEventType(), m.getBizNo(), m.getRoutingKey(),
                m.getPayload(), m.getVersion(), m.getTraceId(), m.getOccurredAt(),
                "PENDING", 0, LocalDateTime.now(), null);
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_outbox_message WHERE status IN ('PENDING','FAILED') "
                        + "ORDER BY created_at LIMIT " + limit, MAPPER);
    }

    @Override
    public void markSent(String eventId) {
        jdbcTemplate.update(
                "UPDATE sys_outbox_message SET status='SENT' WHERE event_id=?", eventId);
    }

    @Override
    public void markFailed(String eventId, String error) {
        jdbcTemplate.update(
                "UPDATE sys_outbox_message SET status='FAILED', attempts=attempts+1, "
                        + "last_error=? WHERE event_id=?",
                truncate(error), eventId);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
