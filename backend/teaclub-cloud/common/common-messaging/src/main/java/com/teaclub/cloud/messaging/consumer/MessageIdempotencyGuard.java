package com.teaclub.cloud.messaging.consumer;

import com.teaclub.cloud.messaging.constant.MqConstants;
import com.teaclub.cloud.messaging.model.EventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 消息消费幂等守卫（Redis 预筛）。
 *
 * <h3>关键：幂等键必须按「消费者」隔离</h3>
 * 键格式为 {@code mq:consumed:{queueName}:{eventId}}，<b>不是</b> {@code mq:consumed:{eventId}}。
 *
 * <p>这是一个真实踩过的坑：ORDER_PAID 事件被 member-service 和 marketing-service
 * <b>同时订阅</b>（fan-out 是正常的），但两个服务共用同一个 Redis 实例。
 * 早期实现只用 eventId 做键，导致：
 * <pre>
 *   14:18:36  marketing-service 消费成功 → 写入 mq:consumed:{eventId}
 *   14:18:36  member-service 看到该键   → 判定"重复消息" → 直接跳过
 * </pre>
 * 结果 marketing 拿到了积分，member 的消费档案永远不更新 ——
 * 而且日志显示的是"重复消息已忽略"，看起来完全正常，排查极其困难。
 *
 * <p>队列名从 {@code messageProperties.getConsumerQueue()} 获取（Spring AMQP 自动填充），
 * 正好标识「哪个消费者」，天然做到一消费者一键。
 *
 * <h3>两步语义</h3>
 * <ol>
 *   <li>{@link #isConsumed} —— 只读判断，已确认消费过才跳过</li>
 *   <li>{@link #markConsumed} —— <b>业务成功之后</b>才写入标记</li>
 * </ol>
 * 失败不留标记，重放时可正常重试。
 * 真正的资金幂等仍由数据库唯一索引保证（Redis 可丢、可重启）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageIdempotencyGuard {

    private static final String KEY_PREFIX = "mq:consumed:";
    private static final String UNKNOWN_QUEUE = "unknown";
    private static final Duration TTL = Duration.ofHours(72);

    private final StringRedisTemplate redisTemplate;

    /**
     * 该消息是否<b>已经成功消费过</b>（按队列隔离）。
     *
     * <p>只读，不写标记：失败重投的消息不会被误判。
     */
    public boolean isConsumed(Message message, EventMessage<?> event) {
        String key = buildKey(message, event);
        if (key == null) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            log.info("[MQ] 该消息已被本消费者处理过，跳过 key={}", key);
            return true;
        }
        return false;
    }

    /**
     * 标记为已消费。<b>必须在业务处理成功之后调用</b>。
     */
    public void markConsumed(Message message, EventMessage<?> event) {
        String key = buildKey(message, event);
        if (key == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, "1", TTL);
        } catch (Exception e) {
            // 标记失败不影响主流程：数据库唯一索引仍是最终防线
            log.warn("[MQ] 消费标记写入失败 key={} err={}", key, e.getMessage());
        }
    }

    /**
     * 构造幂等键：{@code mq:consumed:{queueName}:{eventId}}。
     *
     * @return null 表示无法确定 eventId（此时不参与幂等判断）
     */
    private static String buildKey(Message message, EventMessage<?> event) {
        String eventId = resolveEventId(message, event);
        if (eventId == null) {
            return null;
        }
        String queue = UNKNOWN_QUEUE;
        try {
            String q = message.getMessageProperties().getConsumerQueue();
            if (q != null && !q.isBlank()) {
                queue = q;
            }
        } catch (Exception ignore) {
            // 拿不到队列名时退回 unknown（宁可少隔离也不要抛异常打断消费）
        }
        return KEY_PREFIX + queue + ":" + eventId;
    }

    private static String resolveEventId(Message message, EventMessage<?> event) {
        String eventId = event != null && event.getEventId() != null
                ? event.getEventId()
                : String.valueOf(message.getMessageProperties().getHeader(MqConstants.HEADER_EVENT_ID));
        if (eventId == null || "null".equals(eventId) || eventId.isBlank()) {
            return null;
        }
        return eventId;
    }
}
