package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.entity.MktPointsGoods;
import com.teaclub.cloud.marketing.service.PointsMallService;
import com.teaclub.cloud.marketing.vo.ExchangeResultVO;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 会员端积分商城（小程序）。 */
@Tag(name = "会员端积分商城", description = "商品列表与兑换")
@RestController
@RequestMapping("/api/marketing/points-goods")
@RequiredArgsConstructor
public class PointsMallAppController {

    private final PointsMallService mallService;

    @Operation(summary = "可兑换商品")
    @GetMapping
    public Result<List<MktPointsGoods>> list() {
        return Result.ok(mallService.listAvailable(UserContext.requireMemberId()));
    }

    @Operation(summary = "兑换商品")
    @PostMapping("/{id}/exchange")
    public Result<ExchangeResultVO> exchange(@PathVariable Long id) {
        return Result.ok(mallService.exchange(id, UserContext.requireMemberId()));
    }

    @Operation(summary = "我的兑换记录")
    @GetMapping("/exchanges")
    public Result<List<ExchangeResultVO>> myExchanges(
            @RequestParam(required = false) Integer limit) {
        return Result.ok(mallService.myExchanges(
                UserContext.requireMemberId(), limit == null ? 50 : limit));
    }
}
