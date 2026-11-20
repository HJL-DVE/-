package com.teaclub.cloud.marketing.enums;

/**
 * 等级权益编码。
 *
 * <p>等级<b>定义</b>仍在 member-service（mem_member_level），
 * 这里只放「营销侧权益」：发券、积分倍率、生日礼等。
 */
public enum BenefitCode {

    /** 生日发放指定券模板 */
    BIRTHDAY_COUPON("生日券"),

    /** 积分倍率（benefitValue 存倍率 ×100，如 150 = 1.5 倍） */
    POINT_RATE("积分倍率"),

    /** 生日额外积分 */
    BIRTHDAY_POINTS("生日积分"),

    /** 专属券模板（可多个，逗号分隔模板 ID） */
    EXCLUSIVE_COUPON("专属券"),

    /** 免配送费/优先出杯等展示型权益 */
    PRIORITY_SERVICE("优先服务");

    private final String label;

    BenefitCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
