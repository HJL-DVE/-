package com.teaclub.cloud.messaging.consumer;

import com.teaclub.cloud.core.log.TraceContext;
import com.teaclub.cloud.messaging.model.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

/**
 * 事件消费者基类。
 *
 * <p><b>失败处理策略（方案 A：统一进死信 + 定时补偿重放）</b>
 * <pre>
 *   业务成功 → ack，落幂等标记
 *   任何失败 → nack(requeue=false)，消息经 DLX 进入 xxx.dlq
 *             由 {@code DlqReplayScheduler} 定时重放
 * </pre>
 *
 * <h3>为什么不用 nack(requeue=true)</h3>
 * 实测这条路不可控：
 * <ul>
 *   <li>RabbitMQ 的 {@code basicNack(requeue=true)} <b>不修改消息头</b>，
 *       无法记录重试次数，{{@code x-death}} 只在经 DLX 转发时才写入</li>
 *   <li>结果是「第几次重试」永远是第 1 次，无限重投</li>
 *   <li>重投耗尽后 broker 直接丢弃，队列 messages=0、DLQ=0，
 *       <b>消息凭空消失</b>（实测已复现）</li>
 * </ul>
 * 改成「一律进 DLQ」后，消息<b>永远有落点</b>：
 * 既不会消失，也能被补偿任务重放，重试次数由 DLQ 的 x-death 如实记录。
 *
 * <h3>幂等</h3>
 * Redis 只在<b>成功后</b>标记（{@link MessageIdempotencyGuard#markConsumed}），
 * 失败不留标记，重放时可以正常重试。
 * 真正的资金幂等仍由数据库唯一索引保证。
 */
public abstract class AbstractEventConsumer implements ChannelAwareMessageListener {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired(required = false)
    protected MessageIdempotencyGuard idempotencyGuard;

    @Autowired(required = false)
    protected org.springframework.context.ApplicationContext applicationContext;

    /**
     * 业务处理逻辑，由子类实现。
     *
     * @param event 事件载荷
     * @throws Exception 处理失败时抛出 → 进 DLQ 等重放
     */
    protected abstract void handle(EventMessage<?> event) throws Exception;

    /** 是否启用消费幂等。默认启用。 */
    protected boolean idempotentEnabled() {
        return true;
    }

    @Override
    public void onMessage(Message message, com.rabbitmq.client.Channel channel) throws Exception {
        String eventId = null;
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            EventMessage<?> event = deserialize(message);
            if (event == null) {
                // 无法解析的消息没有重放价值，ack 丢弃并留错误日志
                log.error("消息无法解析，已丢弃 deliveryTag={}", tag);
                channel.basicAck(tag, false);
                return;
            }
            eventId = event.getEventId();

            String traceId = message.getMessageProperties().getHeader("X-Trace-Id");
            if (traceId != null && !traceId.isBlank()) {
                TraceContext.setTraceId(traceId);
            }

            // 幂等：只读判断。失败的消息不会留下标记，重放时能正常重试。
            if (idempotentEnabled() && idempotencyGuard != null
                    && idempotencyGuard.isConsumed(message, event)) {
                log.info("重复消息已忽略: eventId={}", eventId);
                channel.basicAck(tag, false);
                return;
            }

            handle(event);

            // 先 ack 再落幂等标记：
            // 即使标记写失败，数据库唯一索引仍是幂等最终防线，最多多处理一次；
            // 反过来（先标记后 ack）一旦 ack 失败，消息会被永久判为重复。
            channel.basicAck(tag, false);
            if (idempotentEnabled() && idempotencyGuard != null) {
                idempotencyGuard.markConsumed(message, event);
            }
            log.info("消息处理成功: eventId={} type={}", eventId, event.getEventType());

        } catch (BusinessException e) {
            log.error("消息业务处理失败，进入死信待重放: eventId={} msg={}", eventId, e.getMessage());
            releaseClaim(eventId);
            safeNack(channel, message, tag);
        } catch (Exception e) {
            // 系统异常与业务异常同样处理：都进 DLQ。
            // 区别只体现在日志级别与 DLQ 里的 x-death 记录，重放逻辑一视同仁。
            log.error("消息处理异常，进入死信待重放: eventId={}", eventId, e);
            releaseClaim(eventId);
            safeNack(channel, message, tag);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 失败时清理 Inbox 认领。
     *
     * <p>为什么需要：业务事务若已提交认领但后续失败（或调用方没有事务），
     * 认领记录会残留，重放时 {@code tryClaim} 撞唯一键被判为"已处理"直接跳过，
     * 消息<b>永远处理不了且无报错</b>（已实测复现）。
     * 这里显式释放，保证重放能重新认领。
     *
     * <p>业务与认领在同一事务且已回滚时，这条 DELETE 是幂等空操作。
     */
    private void releaseClaim(String eventId) {
        if (!idempotentEnabled() || eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            org.springframework.jdbc.core.JdbcTemplate jdbc =
                    applicationContext == null ? null
                            : applicationContext.getBeanProvider(
                                    org.springframework.jdbc.core.JdbcTemplate.class).getIfAvailable();
            if (jdbc != null) {
                int n = jdbc.update("DELETE FROM sys_inbox_message WHERE event_id = ?", eventId);
                if (n > 0) {
                    log.warn("[Inbox] 已释放认领，允许重放重试 eventId={}", eventId);
                }
            }
        } catch (Exception e) {
            log.debug("[Inbox] 释放认领跳过（无 inbox 表或已回滚）eventId={}: {}", eventId, e.getMessage());
        }
    }

    /**
     * 拒绝消息且不重回队列 → 经队列的 {@code x-dead-letter-exchange} 进入 DLQ。
     *
     * <p>nack 失败（连接已断等）只记日志：此时消息会被 broker 重新投递，
     * 下一轮还能再处理，不算丢失。
     */
    private void safeNack(com.rabbitmq.client.Channel channel, Message message, long tag) {
        try {
            channel.basicNack(tag, false, false);
        } catch (Exception ex) {
            log.warn("nack 失败，消息将由 broker 重新投递: {}", ex.getMessage());
        }
    }

    /** 反序列化消息体。 */
    protected EventMessage<?> deserialize(Message message) {
        try {
            byte[] body = message.getBody();
            String json = new String(body, StandardCharsets.UTF_8);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.findAndRegisterModules();
            var node = mapper.readTree(json);
            // 兼容：某些生产端把 JSON 当字符串发送，会被二次编码成 "{\"a\":1}"
            if (node != null && node.isTextual()) {
                node = mapper.readTree(node.asText());
            }
            if (node == null || node.isNull()) {
                return null;
            }
            return mapper.treeToValue(node, EventMessage.class);
        } catch (Exception e) {
            log.error("消息反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /** 业务异常：表示重试也不会成功。 */
    public static class BusinessException extends RuntimeException {
        public BusinessException(String message) {
            super(message);
        }

        public BusinessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
