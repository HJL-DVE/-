package com.teaclub.cloud.messaging.config;

import com.teaclub.cloud.messaging.constant.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
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
