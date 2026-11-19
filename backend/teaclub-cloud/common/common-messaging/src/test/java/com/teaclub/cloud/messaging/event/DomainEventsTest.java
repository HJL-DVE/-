package com.teaclub.cloud.messaging.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 领域事件路由键单元测试。
 */
class DomainEventsTest {

    private static final List<String> ALL = List.of(
            DomainEvents.MEMBER_REGISTERED,
            DomainEvents.RECHARGE_PAID,
            DomainEvents.WALLET_FROZEN,
            DomainEvents.WALLET_DEDUCTED,
            DomainEvents.PAYMENT_SUCCEEDED,
            DomainEvents.PAYMENT_FAILED,
            DomainEvents.REFUND_SUCCEEDED,
            DomainEvents.ORDER_PAID);

    @Test
    @DisplayName("规格要求的 8 类事件都有稳定路由键")
    void routingKeysResolvable() {
        for (String type : ALL) {
            String rk = DomainEvents.routingKey(type);
            assertNotNull(rk, type + " 无路由键");
            assertFalse(rk.isBlank(), type + " 路由键为空");
        }
    }

    @Test
    @DisplayName("未知事件类型必须拒绝，避免静默投递到错误队列")
    void unknownTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainEvents.routingKey("NOT_A_REAL_EVENT"));
    }
}
