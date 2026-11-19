package com.teaclub.payment.controller;

import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.Result;
import com.teaclub.payment.dto.PayCreateRequest;
import com.teaclub.payment.dto.RefundApplyRequest;
import com.teaclub.payment.service.PaymentService;
import com.teaclub.payment.vo.PayCreateVO;
import com.teaclub.payment.vo.PaymentVO;
import com.teaclub.payment.vo.RefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "支付管理", description = "支付下单、查单、退款、支付流水与通知记录")
@RestController
@RequestMapping("/api/admin/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "发起支付（金额由服务端按业务单确定）")
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('order:create')")
    @AuditLog(module = "支付", value = "发起支付")
    public Result<PayCreateVO> create(@Valid @RequestBody PayCreateRequest request) {
        return Result.ok(paymentService.createPayment(request));
    }

    @Operation(summary = "主动查单")
    @GetMapping("/orders/{paymentNo}/query")
    @PreAuthorize("hasAuthority('payment:query')")
    public Result<PaymentVO> query(@PathVariable String paymentNo) {
        return Result.ok(paymentService.queryPayment(paymentNo));
    }

    @Operation(summary = "支付单分页")
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('payment:order:list')")
    public Result<PageResult<PaymentVO>> orders(@RequestParam(required = false) Integer pageNo,
                                                @RequestParam(required = false) Integer pageSize,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String bizType) {
        return Result.ok(paymentService.pagePayment(pageNo, pageSize, status, bizType));
    }

    @Operation(summary = "申请退款")
    @PostMapping("/refunds")
    @PreAuthorize("hasAuthority('order:refund')")
    @AuditLog(module = "退款", value = "申请退款")
    public Result<RefundVO> applyRefund(@Valid @RequestBody RefundApplyRequest request) {
        return Result.ok(paymentService.applyRefund(request));
    }

    @Operation(summary = "退款单分页")
    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('payment:refund:list')")
    public Result<PageResult<RefundVO>> refunds(@RequestParam(required = false) Integer pageNo,
                                                @RequestParam(required = false) Integer pageSize,
                                                @RequestParam(required = false) String status) {
        return Result.ok(paymentService.pageRefund(pageNo, pageSize, status));
    }

    @Operation(summary = "通知记录分页")
    @GetMapping("/notify-logs")
    @PreAuthorize("hasAuthority('payment:notify:list')")
    public Result<PageResult<Map<String, Object>>> notifyLogs(@RequestParam(required = false) Integer pageNo,
                                                              @RequestParam(required = false) Integer pageSize,
                                                              @RequestParam(required = false) String status) {
        return Result.ok(paymentService.pageNotify(pageNo, pageSize, status));
    }
}
