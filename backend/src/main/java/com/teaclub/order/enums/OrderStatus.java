package com.teaclub.order.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机。非法流转一律拒绝。
 */
@Getter
public enum OrderStatus {

    UNPAID("待支付"),
    PAID("已支付"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    REFUNDING("退款中"),
    REFUNDED("已退款");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            UNPAID, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(COMPLETED, REFUNDING),
            COMPLETED, EnumSet.of(REFUNDING),
            REFUNDING, EnumSet.of(REFUNDED, PAID),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class)
    );

    public boolean canTransitTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void assertCanTransitTo(OrderStatus target, String orderNo) {
        if (!canTransitTo(target)) {
            throw new IllegalStateException(
                    "订单[" + orderNo + "]不允许从 " + this.name() + " 流转到 " + target.name());
        }
    }

    public static OrderStatus of(String value) {
        return valueOf(value);
    }
}
