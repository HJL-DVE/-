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
 * <p>统一处理：
 * <ul>
 *   <li><b>手动 ACK</b>：业务处理成功才 ack，失败走 nack 进入重试/死信</li>
 *   <li><b>消费幂等</b>：以 eventId 占位，重复消息直接 ack 丢弃</li>
 *   <li><b>Trace ID 透传</b>：从消息头恢复链路上下文</li>
 *   <li><b>异常分类</b>：业务异常不重试（直接死信），系统异常才重试</li>
 * </ul>
 *
 * <p><b>幂等说明</b>：Redis 占位只是第一道筛子。
 * 涉及资金的消费必须同时依赖数据库唯一索引或业务单号，
 * 不能仅靠 Redis 去重。
 */
public abstract class AbstractEventConsumer implements ChannelAwareMessageListener {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired(required = false)
    protected MessageIdempotencyGuard idempotencyGuard;

    /**
     * 业务处理逻辑，由子类实现。
     *
     * @param event 事件载荷
     * @throws Exception 处理失败时抛出，触发 nack
     */
    protected abstract void handle(EventMessage<?> event) throws Exception;

    /**
     * 是否启用消费幂等。默认启用。
     */
    protected boolean idempotentEnabled() {
        return true;
    }

    @Override
    public void onMessage(Message message, com.rabbitmq.client.Channel channel) throws Exception {
        String eventId = null;
        try {
            EventMessage<?> event = deserialize(message);
            if (event == null) {
                log.warn("消息无法解析，直接丢弃");
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            eventId = event.getEventId();

            // 恢复链路上下文
            String traceId = message.getMessageProperties().getHeader("X-Trace-Id");
            if (traceId != null && !traceId.isBlank()) {
                TraceContext.setTraceId(traceId);
            }

            // 消费幂等
            if (idempotentEnabled() && idempotencyGuard != null) {
                boolean first = idempotencyGuard.tryMarkConsumed(message, event);
                if (!first) {
                    log.info("重复消息已忽略: eventId={}", eventId);
                    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                    return;
                }
            }

            handle(event);

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            log.info("消息处理成功: eventId={} type={}", eventId, event.getEventType());

        } catch (BusinessException e) {
            // 业务异常：重试无意义，直接进死信
            log.error("消息业务处理失败，转入死信: eventId={} msg={}", eventId, e.getMessage());
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
        } catch (Exception e) {
            // 系统异常：允许重投
            log.error("消息处理异常，将重试: eventId={}", eventId, e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 反序列化消息体。
     */
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

    /**
     * 业务异常：表示重试也不会成功。
     */
    public static class BusinessException extends RuntimeException {
        public BusinessException(String message) {
            super(message);
        }

        public BusinessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
