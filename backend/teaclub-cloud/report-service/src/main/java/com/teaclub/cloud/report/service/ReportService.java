package com.teaclub.cloud.report.service;

import com.teaclub.cloud.report.mapper.ReportMapper;
import com.teaclub.cloud.report.security.ReportScope;
import com.teaclub.cloud.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表聚合服务。
 *
 * <h3>金额口径（重要，与需求书一致）</h3>
 * <ul>
 *   <li><b>充值金额</b> = wal_recharge_order.recharge_amount（本金）
 *       —— 充 100 送 20：充值金额 100，赠送金额 20，到账 120</li>
 *   <li><b>赠送金额</b> = gift_amount，单独展示，<b>不算收款</b></li>
 *   <li><b>模拟支付金额</b> = pay_payment_order.amount（SUCCESS），明确标注为模拟数据；
 *       钱包消费不产生 pay_payment_order，因此不会重复计入</li>
 *   <li><b>钱包消费金额</b> = oms_order.balance_principal_amount + balance_gift_amount</li>
 *   <li><b>销售金额</b> = oms_order.payable_amount（与 order stats 口径一致）</li>
 *   <li>金额单位：分；前端展示除以 100</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper mapper;

    // ==================== 总部驾驶舱 ====================

    public Map<String, Object> dashboardOverview(Long storeId) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Map<String, Object> sales = mapper.salesSummary(brandId, sid, todayStart, null);
        Map<String, Object> recharge = mapper.rechargeSummary(brandId, sid, todayStart, null);
        Map<String, Object> refund = mapper.refundSummary(brandId, sid, todayStart, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storeCount", mapper.countStores(brandId, sid));
        out.put("memberCount", mapper.countMembers(brandId, null, sid));
        out.put("todayMemberCount", mapper.countMembers(brandId, todayStart, sid));
        out.put("todaySales", money(sales.get("sales")));
        out.put("todayOrders", num(sales.get("orders")));
        // 充值金额=本金；赠送金额单列；到账=本金+赠送（口径见类注释）
        out.put("todayRechargeAmount", money(recharge.get("principal")));
        out.put("todayRechargeGiftAmount", money(recharge.get("gift")));
        out.put("todayRechargeCount", num(recharge.get("cnt")));
        out.put("todayRefundAmount", money(refund.get("amount")));
        out.put("todayRefundCount", num(refund.get("cnt")));
        out.put("storeRanking", storeRanking(brandId, sid, todayStart, null));
        return out;
    }

    public Map<String, Object> dashboardTrend(Integer days, Long storeId) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 7 : Math.min(days, 90);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        LocalDateTime until = LocalDate.now().plusDays(1L).atStartOfDay();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", span);
        // 一次查询分别取销售额与订单数，避免重复执行同一条 SQL
        List<Map<String, Object>> trend = mapper.salesTrend(brandId, sid, since, until);
        out.put("salesTrend", fillDays(trend, span, "sales"));
        out.put("ordersTrend", fillDays(trend, span, "orders"));
        out.put("memberGrowth", fillDays(mapper.memberGrowth(brandId, since, sid), span, "cnt"));
        out.put("rechargeTrend", fillDays(mapper.rechargeTrend(brandId, sid, since), span, "principal"));
        out.put("refundTrend", fillDays(mapper.refundTrend(brandId, sid, since), span, "amount"));
        return out;
    }

    // ==================== 门店 ====================

    public Map<String, Object> storeRanking(Long brandId, Long storeId,
                                            LocalDateTime since, LocalDateTime until) {
        List<Map<String, Object>> rows = mapper.salesByStore(brandId, storeId, since, until);
        Map<Long, Map<String, Object>> storeMap = new LinkedHashMap<>();
        for (Map<String, Object> s : mapper.listStores(brandId, storeId)) {
            storeMap.put(num(s.get("id")), s);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            Long sid = num(r.get("storeId"));
            Map<String, Object> st = storeMap.get(sid);
            item.put("storeId", sid);
            item.put("storeName", st == null ? ("门店" + sid)
                    : String.valueOf(st.getOrDefault("storeName", sid)));
            item.put("sales", money(r.get("sales")));
            item.put("orders", num(r.get("orders")));
            item.put("avgOrder", avg(r.get("sales"), r.get("orders")));
            out.add(item);
        }
        return mapOf("list", out);
    }

    public Map<String, Object> storeOverview(Long storeId) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        if (sid == null) {
            // 仅全量账号且未指定门店时，取品牌下第一个门店作为“当前门店”
            List<Map<String, Object>> stores = mapper.listStores(brandId, null);
            sid = stores.isEmpty() ? null : num(stores.get(0).get("id"));
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Map<String, Object> sales = mapper.salesSummary(brandId, sid, todayStart, null);
        Map<String, Object> recharge = mapper.rechargeSummary(brandId, sid, todayStart, null);
        Map<String, Object> refund = mapper.refundSummary(brandId, sid, todayStart, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storeId", sid);
        out.put("sales", money(sales.get("sales")));
        out.put("orders", num(sales.get("orders")));
        out.put("avgOrder", avg(sales.get("sales"), sales.get("orders")));
        out.put("rechargeAmount", money(recharge.get("principal")));
        out.put("rechargeGiftAmount", money(recharge.get("gift")));
        out.put("refundAmount", money(refund.get("amount")));
        out.put("todayNewMembers", mapper.countMembers(brandId, todayStart, sid));

        // 近 30 天热销商品 Top5（销量口径），供门店驾驶舱直接展示
        LocalDateTime since30 = todayStart.minusDays(30);
        var hotRows = mapper.productRank(brandId, sid, since30, null, "qty", 5);
        java.util.List<java.util.Map<String, Object>> hot = new java.util.ArrayList<>();
        for (var row : hotRows) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("spuId", num(row.get("spuId")));
            item.put("name", row.get("name"));
            item.put("qty", num(row.get("qty")));
            item.put("amount", money(row.get("amount")));
            hot.add(item);
        }
        out.put("hotProducts", hot);
        return out;
    }

    // ==================== 销售分析 ====================

    public Map<String, Object> productRanking(Long storeId, Integer days, String metric, int limit) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        // metric 白名单：防 SQL 注入（${} 拼接）
        String m = "amount".equalsIgnoreCase(metric) ? "amount" : "qty";
        return mapOf("metric", m,
                "list", mapper.productRank(brandId, sid,
                        LocalDate.now().minusDays(span - 1L).atStartOfDay(), null, m,
                        limit <= 0 ? 10 : Math.min(limit, 50)));
    }

    public Map<String, Object> storeCompare(Integer days) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(null);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        return mapOf("days", span,
                "list", storeRanking(brandId, sid, since, null).get("list"));
    }

    // ==================== 会员分析 ====================

    public Map<String, Object> memberOverview() {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(null);
        Long brandId = UserContext.require().getBrandId();
        return mapOf(
                "memberCount", mapper.countMembers(brandId, null, sid),
                "levelDistribution", mapper.memberLevelDistribution(brandId, sid),
                "consumeRank", mapper.memberConsumeRank(brandId, sid, 10));
    }

    public Map<String, Object> memberGrowth(Integer days, Long storeId) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        return mapOf("days", span,
                "growth", fillDays(mapper.memberGrowth(brandId, since, sid), span, "cnt"));
    }

    // ==================== 储值 / 支付 / 退款 ====================

    public Map<String, Object> rechargeOverview(Long storeId, Integer days) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();

        Map<String, Object> sum = mapper.rechargeSummary(brandId, sid, since, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", span);
        out.put("count", num(sum.get("cnt")));
        // 明确口径：principal=充值金额（本金），gift=赠送金额， credited=本金+赠送
        out.put("principalAmount", money(sum.get("principal")));
        out.put("giftAmount", money(sum.get("gift")));
        out.put("creditedAmount", money(sum.get("principal")) + money(sum.get("gift")));
        out.put("giftNotCountedAsPayment", true);
        out.put("trend", fillDays(mapper.rechargeTrend(brandId, sid, since), span, "principal"));
        return out;
    }

    public Map<String, Object> paymentOverview(Long storeId, Integer days) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();

        List<Map<String, Object>> byChannel = mapper.payByChannel(brandId, sid, since, null);
        long total = 0;
        for (Map<String, Object> r : byChannel) {
            total += money(r.get("amount"));
        }
        List<Map<String, Object>> shares = new ArrayList<>();
        for (Map<String, Object> r : byChannel) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channel", String.valueOf(r.getOrDefault("channel", "-")));
            item.put("count", num(r.get("cnt")));
            item.put("amount", money(r.get("amount")));
            item.put("share", total > 0
                    ? BigDecimal.valueOf(money(r.get("amount")) * 100L)
                            .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).doubleValue()
                    : 0);
            shares.add(item);
        }

        // 钱包消费：余额本金+赠送抵扣（oms_order），不计入模拟支付收款
        Map<String, Object> wallet = mapper.walletConsumeSummary(brandId, sid, since, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", span);
        out.put("mockPaymentMark", "以下支付金额均为模拟支付数据，非真实收款");
        out.put("byChannel", shares);
        out.put("totalMockPaidAmount", total);
        out.put("walletConsumeAmount", money(wallet.get("amount")));
        return out;
    }

    public Map<String, Object> refundOverview(Long storeId, Integer days) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        Map<String, Object> sum = mapper.refundSummary(brandId, sid, since, null);
        return mapOf("days", span,
                "count", num(sum.get("cnt")),
                "amount", money(sum.get("amount")),
                "trend", fillDays(mapper.refundTrend(brandId, sid, since), span, "amount"));
    }


    /**
     * 每小时订单趋势（0-23 点补零）。
     * 日期参数只取其当天（报表页的「每日小时趋势」概念）。
     */
    public Map<String, Object> hourlyOrders(Long storeId, LocalDate date) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        LocalDateTime day = (date == null ? LocalDate.now() : date).atStartOfDay();
        List<Map<String, Object>> rows = mapper.hourlyOrders(brandId, sid, day, day.plusDays(1L));
        Map<Integer, Map<String, Object>> byHour = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                byHour.put(((Number) r.get("h")).intValue(), r);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> src = byHour.get(h);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", String.format("%02d:00", h));
            item.put("orders", src == null ? 0 : num(src.get("orders")));
            item.put("sales", src == null ? 0 : money(src.get("sales")));
            out.add(item);
        }
        return mapOf("date", day.toLocalDate().toString(), "list", out);
    }

    /** 商品分类销售排行。 */
    public Map<String, Object> categoryRanking(Long storeId, Integer days, String metric, int limit) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        String m = "amount".equalsIgnoreCase(metric) ? "amount" : "qty";
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        return mapOf("metric", m,
                "list", mapper.categoryRank(brandId, sid, since, null, m,
                        limit <= 0 ? 10 : Math.min(limit, 50)));
    }

    /** 商品销售趋势（按日）。 */
    public Map<String, Object> productTrend(Long storeId, Integer days) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(storeId);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        // 一次查询分别取销量与销售额，避免重复执行同一条 SQL
        List<Map<String, Object>> rows = mapper.productTrend(brandId, sid, since);
        return mapOf("days", span,
                "trend", fillDays(rows, span, "qty"),
                "trendAmount", fillDays(rows, span, "amount"));
    }
    // ==================== 营销分析 ====================

    public Map<String, Object> marketingOverview(Integer days) {
        ReportScope scope = ReportScope.current();
        Long sid = scope.resolveStoreId(null);
        Long brandId = UserContext.require().getBrandId();
        int span = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();

        // 券实例表 mkt_coupon 无 store_id 字段，券统计只能按品牌口径；
        // 积分流水有 store_id，按有效门店范围过滤。
        Map<String, Object> coupon = mapper.couponStats(brandId, since, null);
        Map<String, Object> points = mapper.pointsStats(brandId, since, sid);
        long received = num(coupon.get("received"));
        long used = num(coupon.get("used"));

        return mapOf("days", span,
                "couponReceived", received,
                "couponUsed", used,
                "couponUseRate", received > 0
                        ? BigDecimal.valueOf(used * 100L)
                                .divide(BigDecimal.valueOf(received), 1, RoundingMode.HALF_UP).doubleValue()
                        : 0,
                "pointsEarned", num(points.get("earned")),
                "pointsConsumed", num(points.get("consumed")),
                "memberLevelDistribution", mapper.memberLevelDistribution(brandId, sid));
    }

    // ==================== 工具 ====================

    private long money(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private double avg(Object sum, Object count) {
        long s = money(sum);
        long c = num(count);
        return c == 0 ? 0 : BigDecimal.valueOf(s)
                .divide(BigDecimal.valueOf(c), 2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 把趋势查询补齐为连续 N 天（缺失日补 0），前端画折线不断点。 */
    private List<Map<String, Object>> fillDays(List<Map<String, Object>> rows,
                                               int days, String valueKey) {
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                Object d = r.get("d");
                if (d != null) {
                    byDate.put(String.valueOf(d), r);
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate cur = LocalDate.now().minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            String key = cur.toString();
            Map<String, Object> src = byDate.get(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("value", src == null ? 0
                    : num(src.containsKey("sales") && "sales".equals(valueKey)
                            ? src.get("sales") : src.get(valueKey)));
            out.add(item);
            cur = cur.plusDays(1);
        }
        return out;
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}