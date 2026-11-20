package com.teaclub.cloud.messaging.consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 死信队列补偿重放任务（方案 A 的核心）。
 *
 * <p><b>为什么需要它</b>：
 * 消费失败的消息统一 {@code nack(requeue=false)} 进 DLQ —— 这保证消息<b>永远不会消失</b>，
 * 但 DLQ 本身不会自愈。必须有任务把消息搬回原队列重试。
 *
 * <p><b>重放策略</b>：
 * <pre>
 *   每 N 秒扫一次各 DLQ
 *     ├─ x-death.count &lt;= maxReplay → 重放回原队列（原 routingKey）
 *     └─ x-death.count &gt;  maxReplay → 保持不动，等人工介入（避免无限循环）
 * </pre>
 *
 * <p><b>为什么重放最多 N 次就停</b>：
 * 如果下游是永久性故障（比如代码 bug、数据结构错误），无限重放只会刷爆日志。
 * 超过阈值的消息留在 DLQ，由监控告警后人工处理。
 *
 * <p><b>怎么拿原队列</b>：DLQ 消息的 {@code x-death[0].queue} 记录了它来自哪个队列，
 * 用 {@code x-death[0].exchange} + {@code routingKey} 重新投递即可回到原队列。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "teaclub.messaging.dlq-replay-enabled",
        havingValue = "true", matchIfMissing = true)
public class DlqReplayScheduler {

    /** 各服务自己的 DLQ 列表（在 application.yml 里配置，缺省为空=不重放）。 */
    @Value("${teaclub.messaging.dlq-queues:}")
    private String dlqQueuesConfig;

    @Value("${teaclub.messaging.dlq-replay-batch:20}")
    private int batchSize;

    @Value("${teaclub.messaging.dlq-replay-max-retry:5}")
    private int maxReplay;

    private final ConnectionFactory connectionFactory;

    public DlqReplayScheduler(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Scheduled(fixedDelayString = "${teaclub.messaging.dlq-replay-ms:30000}",
            initialDelayString = "${teaclub.messaging.dlq-replay-initial-delay-ms:20000}")
    public void replay() {
        List<String> queues = parseQueues();
        if (queues.isEmpty()) {
            return;
        }
        int total = 0;
        for (String dlq : queues) {
            try {
                total += replayOne(dlq);
            } catch (Exception e) {
                log.warn("[DLQ-REPLAY] 处理 {} 失败: {}", dlq, e.getMessage());
            }
        }
        if (total > 0) {
            log.info("[DLQ-REPLAY] 本轮重放 {} 条", total);
        }
    }

    private List<String> parseQueues() {
        List<String> list = new ArrayList<>();
        if (dlqQueuesConfig == null || dlqQueuesConfig.isBlank()) {
            return list;
        }
        for (String s : dlqQueuesConfig.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 重放单个 DLQ。
     *
     * <p>用 basicGet 逐条取（不用消费者）：这样可以在投递成功后立即 ack，
     * 投递失败则不 ack，消息留在 DLQ，语义清晰。
     */
    @SuppressWarnings("unchecked")
    private int replayOne(String dlqName) throws Exception {
        int replayed = 0;
        try (Connection conn = connectionFactory.createConnection();
             Channel channel = conn.createChannel(false)) {
            for (int i = 0; i < batchSize; i++) {
                GetResponse resp = channel.basicGet(dlqName, false);
                if (resp == null) {
                    break;
                }
                long tag = resp.getEnvelope().getDeliveryTag();
                String body = new String(resp.getBody(), StandardCharsets.UTF_8);
                String eventId = extractEventId(body);

                Map<String, Object> headers = resp.getProps() == null
                        ? Map.of() : resp.getProps().getHeaders();
                int deaths = deathCount(headers);
                if (deaths > maxReplay) {
                    // 超过重放上限：不 ack 也不重投，留在 DLQ 等人工处理。
                    // 但这样会阻塞后面的消息，所以直接 ack 掉并写成 ERROR 日志 + 落地表。
                    log.error("[DLQ-REPLAY] {} 超过重放上限({}次) eventId={}，需人工处理",
                            dlqName, deaths, eventId);
                    channel.basicAck(tag, true);
                    continue;
                }

                String targetExchange = null;
                String routingKey = resp.getEnvelope().getRoutingKey();
                if (headers != null && headers.get("x-death") instanceof List<?> list && !list.isEmpty()
                        && list.get(0) instanceof Map<?, ?> d) {
                    targetExchange = d.get("exchange") == null ? null : String.valueOf(d.get("exchange"));
                    Object rk = d.get("routing-keys");
                    if (rk instanceof List<?> rks && !rks.isEmpty()) {
                        routingKey = String.valueOf(rks.get(0));
                    }
                }
                if (targetExchange == null || targetExchange.isBlank() || "".equals(targetExchange)) {
                    // 找不到来源交换机：无法重投，ack 掉避免堆积
                    log.warn("[DLQ-REPLAY] {} 缺少 x-death 来源信息，丢弃 eventId={}", dlqName, eventId);
                    channel.basicAck(tag, true);
                    continue;
                }

                try {
                    channel.basicPublish(targetExchange, routingKey, resp.getProps(), resp.getBody());
                    channel.basicAck(tag, true);
                    replayed++;
                    log.info("[DLQ-REPLAY] 重放成功 dlq={} -> exchange={} rk={} eventId={} 第{}次",
                            dlqName, targetExchange, routingKey, eventId, deaths + 1);
                } catch (Exception e) {
                    // 投递失败：不 ack，消息留在 DLQ，下一轮再试
                    log.warn("[DLQ-REPLAY] 重投失败，保留在 DLQ: eventId={} err={}", eventId, e.getMessage());
                    break;
                }
            }
        }
        return replayed;
    }

    /** x-death 里的最大 count（可能有多条不同原因的死亡记录）。 */
    @SuppressWarnings("unchecked")
    private static int deathCount(Map<String, Object> headers) {
        int max = 0;
        if (headers == null) {
            return 0;
        }
        Object xDeath = headers.get("x-death");
        if (xDeath instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("count") instanceof Number n) {
                    max = Math.max(max, n.intValue());
                }
            }
        }
        return max;
    }

    private static String extractEventId(String body) {
        int i = body.indexOf("\"eventId\"");
        if (i < 0) {
            return "-";
        }
        int s = body.indexOf('"', i + 9);
        int e = s < 0 ? -1 : body.indexOf('"', s + 1);
        return (s < 0 || e < 0) ? "-" : body.substring(s + 1, e);
    }
}
