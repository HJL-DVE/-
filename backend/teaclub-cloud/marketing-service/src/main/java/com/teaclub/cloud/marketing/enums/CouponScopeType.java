package com.teaclub.cloud.marketing.enums;

/**
 * 优惠券 / 活动的适用范围。
 */
public enum CouponScopeType {

    /** 全品牌通用 */
    ALL("全品牌"),

    /** 限定门店（见 mkt_coupon_template_store） */
    STORE("指定门店"),

    /** 限定商品（见 mkt_coupon_template_spu） */
    GOODS("指定商品");

    private final String label;

    CouponScopeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
