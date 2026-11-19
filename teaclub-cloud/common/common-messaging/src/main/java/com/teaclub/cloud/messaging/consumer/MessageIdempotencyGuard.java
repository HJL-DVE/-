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
 * 消息消费幂等守卫。
 *
 * <p>以 {@code eventId} 为键在 Redis 中占位：
 * <ul>
 *   <li>占位成功 → 首次消费，继续处理</li>
 *   <li>占位失败 → 重复投递，直接丢弃并记录</li>
 * </ul>
 *
 * <p><b>注意</b>：Redis 只是第一道筛子。真正的资金幂等仍依赖数据库唯一索引
 * （{@code wal_wallet_transaction.uk_txn_biz}），
 * 不能用 Redis 作为资金一致性的唯一保障。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageIdempotencyGuard {

    private static final String KEY_PREFIX = "mq:consumed:";
    private static final Duration TTL = Duration.ofHours(72);

    private final StringRedisTemplate redisTemplate;

    /**
     * @return true 表示首次消费（应继续处理）；false 表示重复（应丢弃）
     */
    public boolean tryMarkConsumed(Message message, EventMessage<?> event) {
        String eventId = event != null && event.getEventId() != null
                ? event.getEventId()
                : String.valueOf(message.getMessageProperties().getHeader(MqConstants.HEADER_EVENT_ID));

        if (eventId == null || "null".equals(eventId)) {
            log.warn("[MQ] 消息缺少 eventId，无法判重，按首次处理");
            return true;
        }

        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + eventId, "1", TTL);

        if (Boolean.TRUE.equals(ok)) {
            return true;
        }
        log.info("[MQ] 重复消息已忽略 eventId={}", eventId);
        return false;
    }
}
