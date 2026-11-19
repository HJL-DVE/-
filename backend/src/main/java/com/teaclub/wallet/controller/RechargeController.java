package com.teaclub.wallet.controller;

import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.Result;
import com.teaclub.wallet.dto.RechargeCreateRequest;
import com.teaclub.wallet.dto.RechargeOrderQuery;
import com.teaclub.wallet.service.RechargeService;
import com.teaclub.wallet.vo.RechargeOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "充值订单", description = "创建充值订单、查询充值记录")
@RestController
@RequestMapping("/api/admin/wallet/recharges")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService rechargeService;

    @Operation(summary = "创建充值订单（金额由服务端按方案计算）")
    @PostMapping
    @PreAuthorize("hasAuthority('wallet:recharge:list')")
    @AuditLog(module = "充值", value = "创建充值订单")
    public Result<RechargeOrderVO> create(@Valid @RequestBody RechargeCreateRequest request) {
        return Result.ok(rechargeService.createRecharge(request));
    }

    @Operation(summary = "充值订单分页")
    @GetMapping
    @PreAuthorize("hasAuthority('wallet:recharge:list')")
    public Result<PageResult<RechargeOrderVO>> page(RechargeOrderQuery query) {
        return Result.ok(rechargeService.page(query));
    }

    @Operation(summary = "充值订单详情")
    @GetMapping("/{rechargeNo}")
    @PreAuthorize("hasAuthority('wallet:recharge:list')")
    public Result<RechargeOrderVO> detail(@PathVariable String rechargeNo) {
        return Result.ok(rechargeService.detail(rechargeNo));
    }

    @Operation(summary = "取消未支付的充值订单")
    @PostMapping("/{rechargeNo}/cancel")
    @PreAuthorize("hasAuthority('wallet:recharge:list')")
    @AuditLog(module = "充值", value = "取消充值订单")
    public Result<Void> cancel(@PathVariable String rechargeNo,
                               @RequestBody(required = false) Map<String, String> body) {
        rechargeService.cancel(rechargeNo, body == null ? null : body.get("reason"));
        return Result.ok();
    }
}
