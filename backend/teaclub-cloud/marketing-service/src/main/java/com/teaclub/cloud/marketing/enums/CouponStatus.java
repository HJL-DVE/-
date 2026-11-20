package com.teaclub.cloud.marketing.enums;

/**
 * 优惠券实例状态机。
 *
 * <pre>
 *   UNUSED ──lock──> LOCKED ──use────> USED
 *                      │
 *                      └──release──> UNUSED
 *   UNUSED/LOCKED ──过期──> EXPIRED
 * </pre>
 *
 * <p><b>状态流转一律用条件 UPDATE（WHERE status = 期望前态）</b>，
 * 靠受影响行数判断是否成功，天然抵御并发重复操作。
 */
public enum CouponStatus {

    UNUSED("未使用"),
    /** 已锁定（下单占位，未核销） */
    LOCKED("已锁定"),
    USED("已使用"),
    EXPIRED("已过期");

    private final String label;

    CouponStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 是否可以被锁定（只有未使用、未过期的券能锁）。 */
    public boolean canLock() {
        return this == UNUSED;
    }

    public boolean isTerminal() {
        return this == USED || this == EXPIRED;
    }
}
