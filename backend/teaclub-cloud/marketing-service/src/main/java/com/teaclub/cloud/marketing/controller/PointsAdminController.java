package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.service.PointsService;
import com.teaclub.cloud.marketing.vo.PointsAccountVO;
import com.teaclub.cloud.marketing.vo.PointsTransactionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 积分管理端（Web 后台）。 */
@Tag(name = "积分管理", description = "积分查询、人工调整")
@RestController
@RequestMapping("/api/admin/marketing/points")
@RequiredArgsConstructor
public class PointsAdminController {

    private final PointsService pointsService;

    @Operation(summary = "查询会员积分账户")
    @GetMapping("/account")
    @PreAuthorize("hasAuthority('marketing:points:list')")
    public Result<PointsAccountVO> account(@RequestParam Long memberId) {
        return Result.ok(pointsService.account(memberId));
    }

    @Operation(summary = "会员积分流水")
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('marketing:points:list')")
    public Result<List<PointsTransactionVO>> transactions(@RequestParam Long memberId,
                                                          @RequestParam(required = false) String bizType,
                                                          @RequestParam(required = false) Integer limit) {
        return Result.ok(pointsService.transactions(memberId, bizType, limit == null ? 50 : limit));
    }

    @Operation(summary = "人工调整积分（正加负减）")
    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('marketing:points:adjust')")
    public Result<Boolean> adjust(@RequestParam Long memberId,
                                  @RequestParam Long points,
                                  @RequestParam(required = false) String bizNo,
                                  @RequestParam(required = false) String remark) {
        return Result.ok(pointsService.adjust(memberId, points, bizNo, remark));
    }
}
