package com.teaclub.cloud.messaging.event;

import com.teaclub.cloud.messaging.constant.MqConstants;

/**
 * 领域事件类型常量（规格定义的 8 类重点事件）。
 *
 * <p>每个事件包含：唯一 event_id、业务单号、事件类型、事件版本、产生时间
 * （由 {@link com.teaclub.cloud.messaging.model.EventMessage} 信封统一提供）。
 *
 * <p><b>事件只表达已发生的事实，不承载指令。</b>
 * 跨服务一致性由「本地事务 + Outbox + Saga 补偿」实现，
 * 不采用直接跨库修改数据的方式。
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** 会员注册成功 */
    public static final String MEMBER_REGISTERED = "MEMBER_REGISTERED";
    /** 充值已支付（待入账） */
    public static final String RECHARGE_PAID = "RECHARGE_PAID";
    /** 钱包余额已冻结 */
    public static final String WALLET_FROZEN = "WALLET_FROZEN";
    /** 钱包已扣款 */
    public static final String WALLET_DEDUCTED = "WALLET_DEDUCTED";
    /** 支付成功 */
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    /** 支付失败 */
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    /** 退款成功 */
    public static final String REFUND_SUCCEEDED = "REFUND_SUCCEEDED";
    /** 订单已支付 */
    public static final String ORDER_PAID = "ORDER_PAID";

    /**
     * 事件类型 -> 路由键映射。
     */
    public static String routingKey(String eventType) {
        return switch (eventType) {
            case MEMBER_REGISTERED -> MqConstants.RK_MEMBER_REGISTERED;
            case RECHARGE_PAID -> MqConstants.RK_PAYMENT_RECHARGE_SUCCESS;
            case WALLET_FROZEN -> "wallet.frozen";
            case WALLET_DEDUCTED -> "wallet.deducted";
            case PAYMENT_SUCCEEDED -> MqConstants.RK_PAYMENT_ORDER_SUCCESS;
            case PAYMENT_FAILED -> "payment.failed";
            case REFUND_SUCCEEDED -> MqConstants.RK_REFUND_ORDER_SUCCESS;
            case ORDER_PAID -> "order.paid";
            default -> throw new IllegalArgumentException("未知事件类型: " + eventType);
        };
    }
}
