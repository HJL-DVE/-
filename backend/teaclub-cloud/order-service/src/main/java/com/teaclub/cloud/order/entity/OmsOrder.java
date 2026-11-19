package com.teaclub.cloud.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单（订单域主表）。
 *
 * <p>order-service 独占 order_db，禁止访问其它服务的数据表。
 */
@Data
@TableName("oms_order")
public class OmsOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long brandId;

    /** 订单号（对外唯一标识） */
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

    /** 使用储值实付余额抵扣的金额 */
    private Long balancePrincipalAmount;

    /** 使用储值赠送余额抵扣的金额 */
    private Long balanceGiftAmount;

    /** 订单状态：UNPAID/PAID/COMPLETED/CANCELLED/REFUNDED */
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
