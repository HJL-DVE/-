package com.teaclub.cloud.core.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号生成器（与单体项目保持一致）。
 *
 * <p>微服务场景下各服务独立生成，仍需数据库唯一索引兜底：
 * 充值单号、订单号、支付单号、退款单号均为幂等键。
 */
public final class BizNoGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private BizNoGenerator() {
    }

    private static String base(String prefix) {
        int seq = SEQ.updateAndGet(v -> v >= 999999 ? 1 : v + 1);
        return prefix + LocalDateTime.now().format(TS) + String.format("%06d", seq);
    }

    /**
     * 通用带前缀单号。
     *
     * <p>供新业务（优惠券码、积分兑换单号等）复用，避免每加一个业务就往这里塞一个方法。
     * 前缀建议 2-4 位大写字母，与既有单号风格保持一致。
     *
     * @param prefix 前缀，如 {@code CP}（券）、{@code PX}（兑换）
     */
    public static String of(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("单号前缀不能为空");
        }
        return base(prefix.trim().toUpperCase());
    }

    public static String orderNo()    { return base("TC"); }
    public static String rechargeNo() { return base("RC"); }
    public static String paymentNo()  { return base("PAY"); }
    public static String refundNo()   { return base("RF"); }
    public static String walletTxnNo(){ return base("WT"); }
    public static String approvalNo() {
        return "AP" + LocalDateTime.now().format(TS) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
