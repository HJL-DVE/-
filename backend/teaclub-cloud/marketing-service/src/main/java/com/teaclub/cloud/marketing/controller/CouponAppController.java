package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.service.CouponService;
import com.teaclub.cloud.marketing.vo.CouponVO;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员端优惠券（小程序）。
 *
 * <p>会员身份一律取 {@code UserContext.requireMemberId()}，
 * 请求体/参数里的 memberId 一概忽略，避免越权。
 */
@Tag(name = "会员端优惠券", description = "领券、我的券、可用券")
@RestController
@RequestMapping("/api/marketing/coupons")
@RequiredArgsConstructor
public class CouponAppController {

    private final CouponService couponService;

    @Operation(summary = "领取优惠券")
    @PostMapping("/receive")
    public Result<List<CouponVO>> receive(@RequestParam Long templateId,
                                          @RequestParam(defaultValue = "1") int qty) {
        Long memberId = UserContext.requireMemberId();
        return Result.ok(couponService.receive(templateId, memberId, qty));
    }

    @Operation(summary = "我的优惠券（可按状态过滤）")
    @GetMapping("/mine")
    public Result<List<CouponVO>> mine(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) Integer limit) {
        Long memberId = UserContext.requireMemberId();
        return Result.ok(couponService.listMine(memberId, status, limit == null ? 50 : limit));
    }

    @Operation(summary = "我的可用优惠券")
    @GetMapping("/usable")
    public Result<List<CouponVO>> usable() {
        Long memberId = UserContext.requireMemberId();
        return Result.ok(couponService.listUsable(memberId));
    }
}
