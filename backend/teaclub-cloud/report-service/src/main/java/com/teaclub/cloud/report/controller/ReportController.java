package com.teaclub.cloud.report.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 经营报表（阶段八）。
 *
 * <p><b>数据权限（后端强制）</b>：
 * <ul>
 *   <li>权限点 report:view —— SUPER_ADMIN / HQ_ADMIN / STORE_MANAGER 有，CASHIER 没有</li>
 *   <li>门店范围：allStoreAccess=true 看全部；否则只能看自己绑定门店，越权传 storeId 直接 403</li>
 * </ul>
 *
 * <p>所有金额单位为<b>分</b>；模拟支付金额在返回体里带 mockPaymentMark 说明。
 */
@Tag(name = "经营报表", description = "总部/门店驾驶舱、销售/会员/储值/支付/退款/营销分析")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ==================== 驾驶舱 ====================

    @Operation(summary = "总部经营驾驶舱（今日核心指标 + 门店排行）")
    @GetMapping("/dashboard/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> dashboardOverview(
            @RequestParam(required = false) Long storeId) {
        return Result.ok(reportService.dashboardOverview(storeId));
    }

    @Operation(summary = "经营趋势（默认 7 天，最大 90）")
    @GetMapping("/dashboard/trend")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> dashboardTrend(
            @RequestParam(required = false, defaultValue = "7") Integer days,
            @RequestParam(required = false) Long storeId) {
        return Result.ok(reportService.dashboardTrend(days, storeId));
    }

    @Operation(summary = "门店销售排行")
    @GetMapping("/store/ranking")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> storeRanking(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long storeId) {
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        var since = java.time.LocalDate.now().minusDays(span - 1L).atStartOfDay();
        return Result.ok(reportService.storeRanking(
                com.teaclub.cloud.security.context.UserContext.require().getBrandId(),
                ReportScopeHolder.resolve(storeId), since, null));
    }

    @Operation(summary = "门店经营数据（单店驾驶舱）")
    @GetMapping("/store/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> storeOverview(
            @RequestParam(required = false) Long storeId) {
        return Result.ok(reportService.storeOverview(storeId));
    }

    // ==================== 销售分析 ====================

    @Operation(summary = "商品销量/销售额排行（metric=qty|amount）")
    @GetMapping("/product/ranking")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> productRanking(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false, defaultValue = "qty") String metric,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.ok(reportService.productRanking(storeId, days, metric, limit));
    }

    @Operation(summary = "门店销售对比")
    @GetMapping("/store/compare")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> storeCompare(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.ok(reportService.storeCompare(days));
    }

    // ==================== 会员分析 ====================

    @Operation(summary = "会员总览（总数/等级分布/消费排行）")
    @GetMapping("/member/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> memberOverview() {
        return Result.ok(reportService.memberOverview());
    }

    @Operation(summary = "新增会员趋势")
    @GetMapping("/member/growth")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> memberGrowth(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.ok(reportService.memberGrowth(days));
    }

    // ==================== 储值 / 支付 / 退款 ====================

    @Operation(summary = "储值统计（本金/赠送分离，赠送不计收款）")
    @GetMapping("/recharge/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> rechargeOverview(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.ok(reportService.rechargeOverview(storeId, days));
    }

    @Operation(summary = "支付统计（模拟支付占比 + 钱包消费单列）")
    @GetMapping("/payment/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> paymentOverview(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.ok(reportService.paymentOverview(storeId, days));
    }

    @Operation(summary = "退款统计")
    @GetMapping("/refund/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> refundOverview(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.ok(reportService.refundOverview(storeId, days));
    }

    @Operation(summary = "每小时订单趋势（今日 0-23 点补零）")
    @GetMapping("/store/hourly")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> hourlyOrders(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        return Result.ok(reportService.hourlyOrders(storeId, date));
    }

    @Operation(summary = "商品分类销售排行（metric=qty|amount）")
    @GetMapping("/product/category-ranking")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> categoryRanking(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false, defaultValue = "qty") String metric,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.ok(reportService.categoryRanking(storeId, days, metric, limit));
    }

    @Operation(summary = "商品销售趋势（按日，销量/销售额）")
    @GetMapping("/product/trend")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> productTrend(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.ok(reportService.productTrend(storeId, days));
    }
    // ==================== 营销分析 ====================

    @Operation(summary = "营销分析（券/积分/等级分布）")
    @GetMapping("/marketing/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> marketingOverview(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.ok(reportService.marketingOverview(days));
    }

    /** 让接口层能把越权校验放进 storeRanking 的参数解析。 */
    private static final class ReportScopeHolder {
        static Long resolve(Long requested) {
            return com.teaclub.cloud.report.security.ReportScope.current().resolveStoreId(requested);
        }
    }
}