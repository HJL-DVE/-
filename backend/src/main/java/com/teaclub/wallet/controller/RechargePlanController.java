package com.teaclub.wallet.controller;

import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.Result;
import com.teaclub.wallet.dto.RechargePlanSaveRequest;
import com.teaclub.wallet.service.RechargePlanService;
import com.teaclub.wallet.vo.RechargePlanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "充值方案管理", description = "充值套餐、赠送规则、时间窗口、参与门店与等级限制")
@RestController
@RequestMapping("/api/admin/wallet/plans")
@RequiredArgsConstructor
public class RechargePlanController {

    private final RechargePlanService planService;

    @Operation(summary = "充值方案列表")
    @GetMapping
    @PreAuthorize("hasAuthority('wallet:plan:list')")
    public Result<List<RechargePlanVO>> list(@RequestParam(required = false) String status) {
        return Result.ok(planService.listAll(status));
    }

    @Operation(summary = "某门店下当前会员可用的充值方案")
    @GetMapping("/available")
    public Result<List<RechargePlanVO>> available(@RequestParam(required = false) Long storeId,
                                                  @RequestParam(required = false) Long memberId) {
        return Result.ok(planService.listAvailable(storeId, memberId));
    }

    @Operation(summary = "充值方案详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wallet:plan:list')")
    public Result<RechargePlanVO> detail(@PathVariable Long id) {
        return Result.ok(planService.detail(id));
    }

    @Operation(summary = "新增充值方案")
    @PostMapping
    @PreAuthorize("hasAuthority('wallet:plan:create')")
    @AuditLog(module = "充值方案", value = "新增充值方案")
    public Result<Long> create(@Valid @RequestBody RechargePlanSaveRequest request) {
        return Result.ok(planService.create(request));
    }

    @Operation(summary = "编辑充值方案")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('wallet:plan:update')")
    @AuditLog(module = "充值方案", value = "编辑充值方案")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RechargePlanSaveRequest request) {
        planService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除充值方案")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('wallet:plan:delete')")
    @AuditLog(module = "充值方案", value = "删除充值方案")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }
}
