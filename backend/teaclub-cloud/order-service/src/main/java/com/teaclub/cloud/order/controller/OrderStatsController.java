package com.teaclub.cloud.order.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.order.mapper.OmsOrderMapper;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单统计（总部仪表盘）。
 *
 * <p>只做只读聚合，不改任何业务状态；口径与订单列表一致：
 * 成交额取 {@code payable_amount}，成交状态含
 * {@code PAID / COMPLETED / REFUNDING / REFUNDED}。
 *
 * <p><b>数据权限</b>：非总部账号必须落在自己的门店范围内；
 * 只授权一家门店时可省略 {@code storeId}，授权多家时必须显式选择门店。
 */
@Tag(name = "订单统计", description = "总部仪表盘用的销售聚合")
@RestController
@RequestMapping("/api/admin/order/stats")
@RequiredArgsConstructor
public class OrderStatsController {

    private final OmsOrderMapper orderMapper;

    @Operation(summary = "销售统计汇总（近 N 天）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('order:statistics')")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "7") Integer days) {

        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        int span = days == null || days <= 0 ? 7 : Math.min(days, 90);
        Long effectiveStoreId = resolveStoreId(storeId, scope);

        LocalDateTime since = LocalDateTime.now().minusDays(span).withHour(0).withMinute(0)
                .withSecond(0).withNano(0);
        Long brandId = current.getBrandId();

        long orderCount = orderMapper.countSince(brandId, effectiveStoreId, since);
        long paidOrderCount = orderMapper.countPaidSince(brandId, effectiveStoreId, since);
        Long gmv = orderMapper.sumGmvSince(brandId, effectiveStoreId, since);
        Long balancePay = orderMapper.sumGmvByChannelSince(brandId, effectiveStoreId, since, "BALANCE");
        Long wechatPay = orderMapper.sumGmvByChannelSince(brandId, effectiveStoreId, since, "WECHAT");
        List<Map<String, Object>> trend = orderMapper.dailyTrend(brandId, effectiveStoreId, since);

        long gmvValue = gmv == null ? 0L : gmv;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", span);
        data.put("storeId", effectiveStoreId);
        data.put("orderCount", orderCount);
        data.put("paidOrderCount", paidOrderCount);
        // 钱包支付资金构成：本金抵扣 + 赠送抵扣
        java.util.Map<String, Object> composition =
                orderMapper.sumBalanceCompositionSince(brandId, effectiveStoreId, since);
        long giftPay = composition == null || composition.get("gift") == null
                ? 0L : ((Number) composition.get("gift")).longValue();
        long principalPay = composition == null || composition.get("principal") == null
                ? 0L : ((Number) composition.get("principal")).longValue();
        // 实付口径 = 营业额 - 赠送金抵扣（现金支付 + 钱包本金）；赠送金是营销成本，不计真实收入
        long actualPay = gmvValue - giftPay;

        data.put("gmv", gmvValue);
        data.put("avgOrderAmount", paidOrderCount == 0 ? 0 : gmvValue / paidOrderCount);
        data.put("balancePayAmount", balancePay == null ? 0L : balancePay);
        data.put("wechatPayAmount", wechatPay == null ? 0L : wechatPay);
        data.put("balancePrincipalAmount", principalPay);
        data.put("balanceGiftAmount", giftPay);
        data.put("actualPayAmount", actualPay);
        data.put("dailyTrend", trend);
        return Result.ok(data);
    }

    /**
     * 解析生效门店：
     * 总部账号可不传（不限门店）；门店账号只授权一家时自动取该店，多家时必须显式选择。
     */
    private Long resolveStoreId(Long storeId, StoreScope scope) {
        if (storeId != null) {
            scope.assertAccessible(storeId);
            return storeId;
        }
        if (scope.isAll()) {
            return null;
        }
        if (scope.getStoreIds().size() == 1) {
            return scope.getStoreIds().iterator().next();
        }
        if (scope.isEmptyLimited()) {
            throw new BizException(ResultCode.NO_STORE_PERMISSION, "当前账号未授权任何门店");
        }
        throw new BizException(ResultCode.PARAM_INVALID, "请先选择门店");
    }
}
