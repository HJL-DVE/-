package com.teaclub.order.controller;

import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.Result;
import com.teaclub.order.dto.*;
import com.teaclub.order.service.OrderService;
import com.teaclub.order.vo.OrderQuoteVO;
import com.teaclub.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "订单管理", description = "下单、试算、余额支付、取消、状态流转")
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "订单金额试算（不落单，所有金额服务端计算）")
    @PostMapping("/quote")
    @PreAuthorize("hasAuthority('order:create')")
    public Result<OrderQuoteVO> quote(@Valid @RequestBody OrderQuoteRequest request) {
        return Result.ok(orderService.quote(request));
    }

    @Operation(summary = "创建订单（请求体不接受任何金额字段）")
    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('order:create')")
    @AuditLog(module = "订单", value = "创建订单")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.ok(orderService.create(request));
    }

    @Operation(summary = "余额支付")
    @PostMapping("/orders/{orderNo}/pay-balance")
    @PreAuthorize("hasAuthority('order:pay:balance')")
    @AuditLog(module = "订单", value = "余额支付")
    public Result<OrderVO> payByBalance(@PathVariable String orderNo) {
        return Result.ok(orderService.payByBalance(orderNo));
    }

    @Operation(summary = "订单分页查询（受门店数据权限约束）")
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('order:list')")
    public Result<PageResult<OrderVO>> page(OrderQuery query) {
        return Result.ok(orderService.page(query));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/orders/{orderNo}")
    @PreAuthorize("hasAuthority('order:detail')")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/orders/{orderNo}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    @AuditLog(module = "订单", value = "取消订单")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestBody(required = false) Map<String, String> body) {
        orderService.cancel(orderNo, body == null ? null : body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "订单状态流转")
    @PatchMapping("/orders/{orderNo}/status")
    @PreAuthorize("hasAuthority('order:detail')")
    @AuditLog(module = "订单", value = "订单状态流转")
    public Result<Void> updateStatus(@PathVariable String orderNo,
                                     @Valid @RequestBody OrderStatusUpdateRequest request) {
        orderService.updateStatus(orderNo, request.getTargetStatus(), request.getRemark());
        return Result.ok();
    }
}
