package com.teaclub.cloud.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 订单金额试算结果。前端可用它展示明细，但<b>真实下单时服务端会重新计算</b>。
 */
@Data
@Schema(description = "订单金额试算")
public class OrderQuoteVO {
    @Schema(description = "商品总额（分）")
    private Long totalAmount;
    @Schema(description = "等级折扣优惠（分）")
    private Long levelDiscountAmount;
    @Schema(description = "活动优惠（分）")
    private Long activityDiscountAmount;
    @Schema(description = "券优惠（分）")
    private Long couponAmount;
    @Schema(description = "优惠合计（分）")
    private Long discountAmount;
    @Schema(description = "应付金额（分）")
    private Long payableAmount;

    private Long memberId;
    private String memberLevelName;
    @Schema(description = "会员等级折扣万分比")
    private Integer discountRate;

    private List<OrderItemVO> items;
}
