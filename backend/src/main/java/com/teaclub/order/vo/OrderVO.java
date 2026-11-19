package com.teaclub.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "订单")
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long storeId;
    private String storeName;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private String orderSource;

    @Schema(description = "商品总额（分）")
    private Long totalAmount;
    @Schema(description = "优惠合计（分）")
    private Long discountAmount;
    private Long couponAmount;
    private Long levelDiscountAmount;
    private Long activityDiscountAmount;
    @Schema(description = "应付金额（分）")
    private Long payableAmount;
    @Schema(description = "实付金额（分）")
    private Long paidAmount;
    @Schema(description = "已退款金额（分）")
    private Long refundedAmount;

    @Schema(description = "余额支付中的实付金部分（分）")
    private Long balancePrincipalAmount;
    @Schema(description = "余额支付中的赠送金部分（分）")
    private Long balanceGiftAmount;

    private String status;
    private String statusLabel;
    private String payChannel;
    private String paymentNo;
    private Long memberCouponId;
    private Long pointsEarned;
    private String pickupNo;
    private LocalDateTime payTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String remark;
    private LocalDateTime createTime;

    private List<OrderItemVO> items;
}
