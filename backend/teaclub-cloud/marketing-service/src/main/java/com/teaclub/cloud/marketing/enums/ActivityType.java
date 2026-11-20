package com.teaclub.cloud.marketing.enums;

/**
 * 营销活动类型（规格要求的 8 类）。
 *
 * <p>每类活动的差异体现在 <b>触发时机</b> 与 <b>奖励内容</b> 上，
 * 奖励统一存放在 {@code mkt_activity.config_json}，由 {@link ActivityReward} 解析。
 */
public enum ActivityType {

    /** 新人礼包：注册/首次登录时发放 */
    NEW_MEMBER_GIFT("新人礼包"),

    /** 生日福利：会员生日当天可领 */
    BIRTHDAY("生日福利"),

    /** 节日活动：固定时间区间内参与 */
    FESTIVAL("节日活动"),

    /** 连续签到：依赖签到行为累计 */
    CONTINUOUS_SIGN("连续签到"),

    /** 满赠：订单满额赠券/积分 */
    FULL_GIFT("满赠活动"),

    /** 积分商城：长期开放，用积分兑换 */
    POINTS_MALL("积分商城"),

    /** 指定门店活动：仅在配置的门店生效 */
    STORE_SPECIAL("指定门店活动"),

    /** 沉睡会员召回：长期未消费会员定向发放 */
    SLEEPING_RECALL("沉睡会员召回");

    private final String label;

    ActivityType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 是否依赖订单事件触发。 */
    public boolean triggedByOrder() {
        return this == FULL_GIFT || this == STORE_SPECIAL;
    }

    /** 是否长期有效（不按时间窗口判定）。 */
    public boolean isAlwaysOn() {
        return this == POINTS_MALL;
    }
}
