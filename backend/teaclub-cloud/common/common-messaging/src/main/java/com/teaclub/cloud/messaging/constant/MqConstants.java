package com.teaclub.cloud.messaging.constant;

/**
 * RabbitMQ 统一命名规范与常量。
 *
 * <p>命名规则：
 * <ul>
 *   <li>Exchange: {@code teaclub.<domain>.exchange}（topic 类型）</li>
 *   <li>Queue:    {@code teaclub.<domain>.<consumer>}.queue</li>
 *   <li>Routing:  {@code <domain>.<entity>.<event>}</li>
 *   <li>DLX:      {@code teaclub.<domain>.dlx}</li>
 *   <li>DLQ:      {@code teaclub.<domain>.<consumer>}.dlq</li>
 * </ul>
 */
public final class MqConstants {

    private MqConstants() {
    }

    /** 业务主交换机（topic） */
    public static final String BUSINESS_EXCHANGE = "teaclub.business.exchange";

    /** 业务主交换机路由键通配 */
    public static final String BUSINESS_ROUTING_PREFIX = "teaclub.";

    /** 死信交换机 */
    public static final String DLX_EXCHANGE = "teaclub.dlx.exchange";

    /** 死信路由键后缀 */
    public static final String DLQ_ROUTING_SUFFIX = ".dlq";

    // ===== 领域事件路由键 =====
    /** 支付成功 - 消费订单 */
    public static final String RK_PAYMENT_ORDER_SUCCESS = "payment.order.success";
    /** 支付成功 - 充值 */
    public static final String RK_PAYMENT_RECHARGE_SUCCESS = "payment.recharge.success";
    /** 退款成功 - 订单 */
    public static final String RK_REFUND_ORDER_SUCCESS = "refund.order.success";
    /** 会员注册 */
    public static final String RK_MEMBER_REGISTERED = "member.registered";
    /** 订单已支付（余额/微信统一） */
    public static final String RK_ORDER_PAID = "order.paid";
    /** 钱包已扣款 */
    public static final String RK_WALLET_DEDUCTED = "wallet.deducted";
    /** 钱包已冻结 */
    public static final String RK_WALLET_FROZEN = "wallet.frozen";
    /** 充值已支付 */
    public static final String RK_RECHARGE_PAID = "recharge.paid";
    /** 门店状态变更 */
    public static final String RK_STORE_STATUS_CHANGED = "store.status.changed";

    // ===== 消费队列 =====
    public static final String Q_ORDER_PAY_SUCCESS = "teaclub.order.pay-success.queue";
    public static final String Q_WALLET_RECHARGE_SUCCESS = "teaclub.wallet.recharge-success.queue";
    public static final String Q_ORDER_REFUND_SUCCESS = "teaclub.order.refund-success.queue";
    /** 会员服务：消费订单已支付事件（累加消费档案） */
    public static final String Q_MEMBER_ORDER_PAID = "teaclub.member.order-paid.queue";
    /**
     * 营销服务：消费订单已支付事件（发放消费积分）。
     *
     * <p>与会员服务用<b>不同队列</b>消费同一个 routingKey：
     * topic 交换机会把消息投给所有匹配队列，各服务互不阻塞、互不影响。
     */
    public static final String Q_MARKETING_ORDER_PAID = "teaclub.marketing.order-paid.queue";
    /** 营销服务：消费退款成功事件（积分冲正） */
    public static final String Q_MARKETING_REFUND_SUCCESS = "teaclub.marketing.refund-success.queue";
    /** 营销服务：优惠券过期处理相关的延迟/补偿（预留） */
    public static final String Q_MARKETING_COUPON_EXPIRE = "teaclub.marketing.coupon-expire.queue";

    // ===== 消息头 =====
    public static final String HEADER_EVENT_ID = "x-event-id";
    public static final String HEADER_EVENT_TYPE = "x-event-type";
    public static final String HEADER_TRACE_ID = "x-trace-id";
    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final String HEADER_OCCURRED_AT = "x-occurred-at";
}
