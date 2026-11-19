package com.teaclub.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_refund_order")
public class PayRefundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String refundNo;
    private String paymentNo;
    private String bizType;
    private String bizNo;
    private Long storeId;
    private Long memberId;
    private String channel;
    private Long refundAmount;
    private Long refundPrincipalAmount;
    private Long refundGiftAmount;
    private Long refundCashAmount;
    private String reason;
    private String status;
    private String wxRefundId;
    private String refundStatusWx;
    private LocalDateTime successTime;
    private String failReason;
    private Long applyUserId;
    private Long auditUserId;
    private LocalDateTime auditTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
