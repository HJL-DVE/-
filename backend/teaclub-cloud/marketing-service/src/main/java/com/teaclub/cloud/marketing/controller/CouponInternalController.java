package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.service.CouponService;
import com.teaclub.cloud.marketing.vo.CouponQuoteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券内部接口（供 order-service 调用）。
 *
 * <p>路径含 {@code /internal}，由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}，
 * 外部调用一律 401。
 *
 * <p><b>调用时序</b>：
 * <pre>
 *   下单试算  → POST /api/marketing/internal/coupons/quote      （不落库）
 *   创建订单  → POST /api/marketing/internal/coupons/lock        （UNUSED→LOCKED）
 *   支付成功  → POST /api/marketing/internal/coupons/use         （LOCKED→USED，幂等）
 *   订单取消  → POST /api/marketing/internal/coupons/release     （LOCKED→UNUSED）
 * </pre>
 */
@Tag(name = "优惠券内部接口", description = "供订单服务调用")
@RestController
@RequestMapping("/api/marketing/internal/coupons")
@RequiredArgsConstructor
public class CouponInternalController {

    private final CouponService couponService;

    @Operation(summary = "试算抵扣金额（不修改状态）")
    @PostMapping("/quote")
    public Result<CouponQuoteVO> quote(@RequestParam(required = false) String couponNo,
                                       @RequestParam Long memberId,
                                       @RequestParam(required = false) Long storeId,
                                       @RequestParam Long orderAmount,
                                       @RequestParam(required = false) List<Long> spuIds) {
        return Result.ok(couponService.quote(couponNo, memberId, storeId, orderAmount, spuIds));
    }

    @Operation(summary = "锁定券（下单占位）")
    @PostMapping("/lock")
    public Result<Boolean> lock(@RequestParam String couponNo,
                                @RequestParam Long memberId,
                                @RequestParam String orderNo) {
        return Result.ok(couponService.lock(couponNo, memberId, orderNo));
    }

    @Operation(summary = "核销券（支付成功，幂等）")
    @PostMapping("/use")
    public Result<Boolean> use(@RequestParam String couponNo,
                               @RequestParam String orderNo,
                               @RequestParam(required = false) Long discountAmount) {
        return Result.ok(couponService.use(couponNo, orderNo, discountAmount));
    }

    @Operation(summary = "释放券（订单取消）")
    @PostMapping("/release")
    public Result<Boolean> release(@RequestParam String orderNo) {
        return Result.ok(couponService.releaseByOrder(orderNo));
    }
}
