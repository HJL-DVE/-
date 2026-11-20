package com.teaclub.cloud.marketing.enums;

/**
 * 优惠券类型。
 *
 * <p>行为差异集中在这里，业务代码用 {@link #isDiscount()} 等方法分支，
 * 避免到处写字符串比较。
 */
public enum CouponType {

    /** 满减券：满 {@code thresholdAmount} 减 {@code faceValue} */
    FULL_REDUCTION("满减券"),

    /** 折扣券：按 {@code discountRate} 打折，可封顶 {@code maxDiscountAmount} */
    DISCOUNT("折扣券"),

    /** 无门槛券：直接抵 {@code faceValue}，无门槛限制 */
    NO_THRESHOLD("无门槛券"),

    /** 商品兑换券：兑换指定 SPU/SKU，不抵扣金额 */
    GOODS_EXCHANGE("商品兑换券"),

    /** 新人券：仅新会员可领（领取时额外校验） */
    NEW_MEMBER("新人券"),

    /** 生日券：仅生日期间可用 */
    BIRTHDAY("生日券");

    private final String label;

    CouponType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 折扣券按比例计算抵扣，其余按面额。 */
    public boolean isDiscount() {
        return this == DISCOUNT;
    }

    public boolean isExchange() {
        return this == GOODS_EXCHANGE;
    }

    /** 是否需要满足门槛。 */
    public boolean hasThreshold() {
        return this == FULL_REDUCTION;
    }
}
