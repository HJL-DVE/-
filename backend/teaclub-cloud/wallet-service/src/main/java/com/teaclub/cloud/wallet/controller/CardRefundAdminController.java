package com.teaclub.cloud.wallet.controller;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.wallet.dto.CardRefundAuditRequest;
import com.teaclub.cloud.wallet.service.CardRefundService;
import com.teaclub.cloud.wallet.vo.CardRefundVO;
import com.teaclub.cloud.web.annotation.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台退卡审核。
 *
 * <p>权限：{@code wallet:card-refund:audit}，默认分配给店长与总部。
 * 收银员<b>无权审核</b>（大额资金流出需店长以上决策）。
 */
@Tag(name = "后台退卡审核", description = "退卡申请列表与审核")
@RestController
@RequestMapping("/api/admin/wallet/card-refunds")
@RequiredArgsConstructor
public class CardRefundAdminController {

    private final CardRefundService cardRefundService;

    @Operation(summary = "退卡申请分页")
    @GetMapping
    @PreAuthorize("hasAuthority('wallet:card-refund:list')")
    public Result<PageResult<CardRefundVO>> page(@RequestParam(required = false) Integer pageNo,
                                                  @RequestParam(required = false) Integer pageSize,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long memberId) {
        return Result.ok(cardRefundService.page(pageNo, pageSize, status, memberId));
    }

    @Operation(summary = "审核退卡申请")
    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('wallet:card-refund:audit')")
    @AuditLog(module = "储值管理", value = "审核退卡申请")
    public Result<CardRefundVO> audit(@PathVariable Long id,
                                       @RequestBody CardRefundAuditRequest request) {
        return Result.ok(cardRefundService.audit(id, request));
    }
}
