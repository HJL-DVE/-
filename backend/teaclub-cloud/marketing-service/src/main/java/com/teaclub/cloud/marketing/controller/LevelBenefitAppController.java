package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.entity.MktLevelBenefit;
import com.teaclub.cloud.marketing.service.LevelBenefitService;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员端等级权益（小程序）。
 *
 * <p>会员的等级 ID 由 member-service 提供，前端先调
 * {@code GET /api/member/profile} 拿到 levelId，再来查权益。
 */
@Tag(name = "会员端等级权益", description = "按等级查营销权益")
@RestController
@RequestMapping("/api/marketing/level-benefits")
@RequiredArgsConstructor
public class LevelBenefitAppController {

    private final LevelBenefitService benefitService;

    @Operation(summary = "某等级的营销权益")
    @GetMapping("/level/{levelId}")
    public Result<List<MktLevelBenefit>> listByLevel(@PathVariable Long levelId) {
        UserContext.requireMemberId();
        return Result.ok(benefitService.listByLevel(levelId));
    }
}
