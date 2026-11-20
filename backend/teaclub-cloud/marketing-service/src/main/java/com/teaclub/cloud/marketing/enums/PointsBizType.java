package com.teaclub.cloud.marketing.enums;

/**
 * 积分流水业务类型。
 *
 * <p>{@code uk_points_biz(brand_id, biz_type, biz_no)} 依赖这些类型做唯一性约束，
 * 因此<b>增加新类型时要确认 bizNo 的语义唯一</b>。
 */
public enum PointsBizType {

    /** 订单消费得积分（bizNo = 订单号） */
    ORDER_EARN("消费得积分"),

    /** 订单退款冲正（bizNo = 原订单号） */
    ORDER_REFUND_REVERSE("退款冲正"),

    /** 签到（bizNo = yyyyMMdd） */
    SIGN_IN("签到"),

    /** 活动奖励（bizNo = 活动编号+会员+业务单） */
    ACTIVITY("活动奖励"),

    /** 积分兑换扣减（bizNo = 兑换单号） */
    EXCHANGE("积分兑换"),

    /** 人工扣减（bizNo = 操作单号） */
    DEDUCT("人工扣减"),

    /** 过期扣除（bizNo = 批次号） */
    EXPIRE("积分过期"),

    /** 人工调整（bizNo = 操作单号） */
    ADJUST("人工调整");

    private final String label;

    PointsBizType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
