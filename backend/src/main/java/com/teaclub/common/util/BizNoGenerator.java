package com.teaclub.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号生成器。
 *
 * <p>格式：前缀 + yyyyMMddHHmmss + 6 位序列/随机。唯一索引兜底防重。
 */
public final class BizNoGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private BizNoGenerator() {
    }

    private static String base(String prefix) {
        int seq = SEQ.updateAndGet(v -> v >= 999999 ? 1 : v + 1);
        return prefix + LocalDateTime.now().format(TS)
                + String.format("%06d", seq);
    }

    public static String orderNo() {
        return base("TC");
    }

    public static String rechargeNo() {
        return base("RC");
    }

    public static String paymentNo() {
        return base("PAY");
    }

    public static String refundNo() {
        return base("RF");
    }

    public static String walletTxnNo() {
        return base("WT");
    }

    public static String approvalNo() {
        return "AP" + LocalDateTime.now().format(TS)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
