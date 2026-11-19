package com.teaclub.cloud.payment.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * payment-service 调用 order-service。
 *
 * <p>order_db 归 order-service 独占，payment-service 不得直接访问。
 */
@FeignClient(name = "order-service", path = "/api/order/internal", contextId = "paymentOrderInternalClient")
public interface OrderInternalClient {

    /** 按订单号查询订单（校验金额与状态）。 */
    @GetMapping("/order/{orderNo}")
    Result<Map<String, Object>> order(@PathVariable("orderNo") String orderNo);

    /** 标记订单支付中。 */
    @PostMapping("/order/{orderNo}/mark-paying")
    Result<Void> markPaying(@PathVariable("orderNo") String orderNo,
                            @RequestBody Map<String, Object> body);

    /** 标记订单已支付。 */
    @PostMapping("/order/{orderNo}/mark-paid")
    Result<Void> markPaid(@PathVariable("orderNo") String orderNo,
                          @RequestBody Map<String, Object> body);

    /** 订单退款申请。 */
    @PostMapping("/order/{orderNo}/apply-refund")
    Result<Void> applyRefund(@PathVariable("orderNo") String orderNo,
                             @RequestBody Map<String, Object> body);
}
