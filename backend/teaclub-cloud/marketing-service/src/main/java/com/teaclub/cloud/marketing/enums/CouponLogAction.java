package com.teaclub.cloud.marketing.enums;

/**
 * 优惠券操作动作（写入 mkt_coupon_log）。
 */
public enum CouponLogAction {

    LOCK("锁定"),
    UNLOCK("解锁"),
    USE("核销"),
    RELEASE("释放"),
    EXPIRE("过期");

    private final String label;

    CouponLogAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
