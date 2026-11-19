package com.teaclub.cloud.order.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.order.dto.OrderCreateRequest;
import com.teaclub.cloud.order.dto.OrderQuery;
import com.teaclub.cloud.order.dto.OrderQuoteRequest;
import com.teaclub.cloud.order.vo.OrderQuoteVO;
import com.teaclub.cloud.order.vo.OrderVO;

public interface OrderService {

    /** 金额试算（不落单） */
    OrderQuoteVO quote(OrderQuoteRequest request);

    /** 创建订单（金额服务端重算） */
    OrderVO create(OrderCreateRequest request);

    /** 余额支付 */
    OrderVO payByBalance(String orderNo);

    /** 微信支付：创建支付单（返回支付参数由 PaymentService 处理） */
    OrderVO markPaying(String orderNo, String paymentNo, String payChannel);

    /** 支付成功确认（由支付通知调用，幂等） */
    void processPaySuccess(String orderNo, String paymentNo);

    /** 取消订单 */
    void cancel(String orderNo, String reason);

    /** 订单详情 */
    OrderVO detail(String orderNo);

    /** 订单分页 */
    PageResult<OrderVO> page(OrderQuery query);

    /** 状态流转（人工） */
    void updateStatus(String orderNo, String targetStatus, String remark);

    /** 记录已退款金额（退款成功后调用） */
    void applyRefund(String orderNo, Long refundAmount, boolean fullyRefunded);
}
