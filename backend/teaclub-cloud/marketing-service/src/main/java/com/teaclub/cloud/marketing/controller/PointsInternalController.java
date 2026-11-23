package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.service.PointsService;
import com.teaclub.cloud.marketing.vo.PointsAccountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 积分内部接口（供 order-service 调用）。
 *
 * <p>路径含 {@code /internal}，由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}，
 * 外部调用一律 401。
 *
 * <p><b>调用时序</b>（简化方案：试算只查余额不扣，支付成功一次性扣，取消订单不扣）：
 * <pre>
 *   下单试算  → GET  /api/marketing/internal/points/balance     （查余额，不扣）
 *   支付成功  → POST /api/marketing/internal/points/deduct      （按订单号幂等扣减）
 * </pre>
 */
@Tag(name = "积分内部接口", description = "供订单服务调用")
@RestController
@RequestMapping("/api/marketing/internal/points")
@RequiredArgsConstructor
public class PointsInternalController {

    private final PointsService pointsService;

    @Operation(summary = "查询会员可用积分（仅查询，不扣减）")
    @GetMapping("/balance")
    public Result<Long> balance(@RequestParam Long memberId) {
        PointsAccountVO account = pointsService.account(memberId);
        long available = account == null || account.getTotalPoints() == null
                ? 0L : account.getTotalPoints();
        return Result.ok(available);
    }

    @Operation(summary = "扣减积分（支付成功，按订单号幂等）")
    @PostMapping("/deduct")
    public Result<Boolean> deduct(@RequestParam Long memberId,
                                  @RequestParam long points,
                                  @RequestParam String orderNo) {
        if (points <= 0) {
            return Result.ok(Boolean.FALSE);
        }
        // 负数表示扣减；bizNo 带订单号，adjust 内部唯一索引保证同一订单只扣一次
        return Result.ok(pointsService.adjust(memberId, -points,
                "POINT_DEDUCT-" + orderNo, "订单积分抵扣"));
    }
}
