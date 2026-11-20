package com.teaclub.cloud.messaging.config;

import com.teaclub.cloud.messaging.constant.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 基础设施声明。
 *
 * <p>包含：
 * <ul>
 *   <li>业务 topic 交换机</li>
 *   <li>死信交换机与死信队列</li>
 *   <li>各业务队列（带 DLX 参数）</li>
 *   <li>Publisher Confirm / Returns 回调</li>
 *   <li>JSON 消息转换器</li>
 * </ul>
 */
@Configuration
public class RabbitMqConfig {

    // ==================== 交换机 ====================

    @Bean
    public TopicExchange businessExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.BUSINESS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(MqConstants.DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    // ==================== 死信队列 ====================

    @Bean
    public Queue orderPaySuccessDlq() {
        return QueueBuilder.durable(MqConstants.Q_ORDER_PAY_SUCCESS + MqConstants.DLQ_ROUTING_SUFFIX).build();
    }

    @Bean
    public Queue walletRechargeSuccessDlq() {
        return QueueBuilder.durable(MqConstants.Q_WALLET_RECHARGE_SUCCESS + MqConstants.DLQ_ROUTING_SUFFIX).build();
    }

    @Bean
    public Binding orderPaySuccessDlqBinding() {
        return BindingBuilder.bind(orderPaySuccessDlq())
                .to(dlxExchange())
                .with(MqConstants.Q_ORDER_PAY_SUCCESS + MqConstants.DLQ_ROUTING_SUFFIX);
    }

    @Bean
    public Binding walletRechargeSuccessDlqBinding() {
        return BindingBuilder.bind(walletRechargeSuccessDlq())
                .to(dlxExchange())
                .with(MqConstants.Q_WALLET_RECHARGE_SUCCESS + MqConstants.DLQ_ROUTING_SUFFIX);
    }

    // ==================== 业务队列（带死信转发）====================

    /** 订单服务：消费支付成功事件 */
    @Bean
    public Queue orderPaySuccessQueue() {
        return businessQueue(MqConstants.Q_ORDER_PAY_SUCCESS);
    }

    /** 钱包服务：消费充值成功事件 */
    @Bean
    public Queue walletRechargeSuccessQueue() {
        return businessQueue(MqConstants.Q_WALLET_RECHARGE_SUCCESS);
    }

    /** 会员服务：消费订单已支付事件 */
    @Bean
    public Queue memberOrderPaidQueue() {
        return businessQueue(MqConstants.Q_MEMBER_ORDER_PAID);
    }

    @Bean
    public Queue memberOrderPaidDlq() {
        return QueueBuilder.durable(MqConstants.Q_MEMBER_ORDER_PAID + MqConstants.DLQ_ROUTING_SUFFIX).build();
    }

    @Bean
    public Binding memberOrderPaidDlqBinding() {
        return BindingBuilder.bind(memberOrderPaidDlq())
                .to(dlxExchange())
                .with(MqConstants.Q_MEMBER_ORDER_PAID + MqConstants.DLQ_ROUTING_SUFFIX);
    }

    @Bean
    public Binding memberOrderPaidBinding() {
        return BindingBuilder.bind(memberOrderPaidQueue())
                .to(businessExchange())
                .with(MqConstants.RK_ORDER_PAID);
    }

    // ==================== 营销服务队列 ====================

    /** 营销服务：消费订单已支付事件（发积分） */
    @Bean
    public Queue marketingOrderPaidQueue() {
        return businessQueue(MqConstants.Q_MARKETING_ORDER_PAID);
    }

    @Bean
    public Queue marketingOrderPaidDlq() {
        return QueueBuilder.durable(MqConstants.Q_MARKETING_ORDER_PAID + MqConstants.DLQ_ROUTING_SUFFIX).build();
    }

    @Bean
    public Binding marketingOrderPaidDlqBinding() {
        return BindingBuilder.bind(marketingOrderPaidDlq())
                .to(dlxExchange())
                .with(MqConstants.Q_MARKETING_ORDER_PAID + MqConstants.DLQ_ROUTING_SUFFIX);
    }

    @Bean
    public Binding marketingOrderPaidBinding() {
        return BindingBuilder.bind(marketingOrderPaidQueue())
                .to(businessExchange())
                .with(MqConstants.RK_ORDER_PAID);
    }

    /** 营销服务：消费退款成功事件（积分冲正） */
    @Bean
    public Queue marketingRefundSuccessQueue() {
        return businessQueue(MqConstants.Q_MARKETING_REFUND_SUCCESS);
    }

    @Bean
    public Queue marketingRefundSuccessDlq() {
        return QueueBuilder.durable(MqConstants.Q_MARKETING_REFUND_SUCCESS + MqConstants.DLQ_ROUTING_SUFFIX).build();
    }

    @Bean
    public Binding marketingRefundSuccessDlqBinding() {
        return BindingBuilder.bind(marketingRefundSuccessDlq())
                .to(dlxExchange())
                .with(MqConstants.Q_MARKETING_REFUND_SUCCESS + MqConstants.DLQ_ROUTING_SUFFIX);
    }

    @Bean
    public Binding marketingRefundSuccessBinding() {
        return BindingBuilder.bind(marketingRefundSuccessQueue())
                .to(businessExchange())
                .with(MqConstants.RK_REFUND_ORDER_SUCCESS);
    }

    @Bean
    public Binding orderPaySuccessBinding() {
        return BindingBuilder.bind(orderPaySuccessQueue())
                .to(businessExchange())
                .with(MqConstants.RK_PAYMENT_ORDER_SUCCESS);
    }

    @Bean
    public Binding walletRechargeSuccessBinding() {
        return BindingBuilder.bind(walletRechargeSuccessQueue())
                .to(businessExchange())
                .with(MqConstants.RK_PAYMENT_RECHARGE_SUCCESS);
    }

    /**
     * 构造带死信配置的业务队列。
     */
    private Queue businessQueue(String name) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", MqConstants.DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", name + MqConstants.DLQ_ROUTING_SUFFIX);
        args.put("x-message-ttl", 600_000);   // 10 分钟未消费转死信
        args.put("x-max-length", 100_000);
        return QueueBuilder.durable(name).withArguments(args).build();
    }

    // ==================== 模板与转换器 ====================

    /**
     * JSON 消息转换器。
     *
     * <p><b>为什么用 INFERRED 而不是默认的 TYPE_ID</b>：
     * 默认会写 {@code __TypeId__} 头并在消费端按该类型反序列化，
     * 一旦生产端发的是 {@code JsonNode}/{@code Map} 就会因"不可信包"被拒。
     * 本项目消费端统一接收原始 {@code Message} 并自行解析，
     * 因此让转换器<b>按目标类型推断</b>、忽略类型头，最稳妥。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /**
     * RabbitTemplate 开启 Publisher Confirm 与 Returns，
     * 发送失败时可感知并记录，避免消息静默丢失。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                org.slf4j.LoggerFactory.getLogger(RabbitMqConfig.class)
                        .error("[MQ] 消息未被 Broker 确认 correlation={} cause={}", correlation, cause);
            }
        });
        template.setReturnsCallback(returned ->
                org.slf4j.LoggerFactory.getLogger(RabbitMqConfig.class)
                        .error("[MQ] 消息无法路由 replyCode={} replyText={} exchange={} routingKey={}",
                                returned.getReplyCode(), returned.getReplyText(),
                                returned.getExchange(), returned.getRoutingKey()));
        return template;
    }
}
