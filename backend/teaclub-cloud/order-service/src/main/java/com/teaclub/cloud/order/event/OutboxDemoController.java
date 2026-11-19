package com.teaclub.cloud.order.event;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.messaging.event.DomainEvents;
import com.teaclub.cloud.messaging.outbox.OutboxService;
import com.teaclub.cloud.messaging.outbox.OutboxStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbox 事件机制演示端点（课程设计验证用）。
 *
 * <p>写入一个 ORDER_PAID 事件到 outbox 表，
 * 由 OutboxRelay 异步投递到 RabbitMQ。
 */
@Tag(name = "Outbox 演示", description = "验证事务发件箱机制")
@RestController
@RequestMapping("/api/order/internal/outbox-demo")
@RequiredArgsConstructor
@Slf4j
public class OutboxDemoController {

    private final OutboxService outboxService;
    private final OutboxStore outboxStore;

    @Operation(summary = "登记一个 ORDER_PAID 事件")
    @GetMapping("/enqueue")
    @Transactional
    public Result<Map<String, Object>> enqueue() {
        String bizNo = "DEMO-ORDER-" + System.currentTimeMillis();
        String eventId = outboxService.enqueue(
                DomainEvents.ORDER_PAID, bizNo, Map.of("orderNo", bizNo, "amount", 100));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", eventId);
        data.put("eventType", DomainEvents.ORDER_PAID);
        data.put("bizNo", bizNo);
        data.put("routingKey", DomainEvents.routingKey(DomainEvents.ORDER_PAID));
        log.info("Outbox 演示事件已登记: eventId={} bizNo={}", eventId, bizNo);
        return Result.ok(data);
    }

    @Operation(summary = "查询 Outbox 待投递消息数")
    @GetMapping("/pending")
    public Result<Integer> pending() {
        return Result.ok(outboxStore.findPending(100).size());
    }
}
