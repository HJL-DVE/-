package com.teaclub.cloud.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oms_order")
public class OmsOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String orderNo;
    private Long storeId;
    private Long memberId;
    private String orderSource;
    private Long totalAmount;
    private Long discountAmount;
    private Long couponAmount;
    private Long levelDiscountAmount;
    private Long activityDiscountAmount;
    private Long payableAmount;
    private Long paidAmount;
    private Long refundedAmount;
    private Long balancePrincipalAmount;
    private Long balanceGiftAmount;
    private String status;
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
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
