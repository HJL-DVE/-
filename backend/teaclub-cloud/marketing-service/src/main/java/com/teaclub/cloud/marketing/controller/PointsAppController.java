package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.service.PointsService;
import com.teaclub.cloud.marketing.vo.PointsAccountVO;
import com.teaclub.cloud.marketing.vo.PointsTransactionVO;
import com.teaclub.cloud.marketing.vo.SignInResultVO;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 会员端积分（小程序）。 */
@Tag(name = "会员端积分", description = "积分账户、流水、签到")
@RestController
@RequestMapping("/api/marketing/points")
@RequiredArgsConstructor
public class PointsAppController {

    private final PointsService pointsService;

    @Operation(summary = "我的积分账户")
    @GetMapping("/account")
    public Result<PointsAccountVO> account() {
        return Result.ok(pointsService.account(UserContext.requireMemberId()));
    }

    @Operation(summary = "我的积分流水")
    @GetMapping("/transactions")
    public Result<List<PointsTransactionVO>> transactions(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(pointsService.transactions(
                UserContext.requireMemberId(), bizType, limit == null ? 50 : limit));
    }

    @Operation(summary = "签到")
    @PostMapping("/sign-in")
    public Result<SignInResultVO> signIn() {
        return Result.ok(pointsService.signIn(UserContext.requireMemberId()));
    }
}
