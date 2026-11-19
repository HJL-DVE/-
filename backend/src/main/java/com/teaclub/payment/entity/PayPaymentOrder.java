package com.teaclub.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_payment_order")
public class PayPaymentOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String paymentNo;
    private String bizType;
    private String bizNo;
    private Long storeId;
    private Long memberId;
    private String channel;
    private String provider;
    private Long amount;
    private String currency;
    private String status;
    private String prepayId;
    private String transactionId;
    private String codeUrl;
    private String payerOpenid;
    private LocalDateTime successTime;
    private String failReason;
    private LocalDateTime expireTime;
    private Integer notifyReceived;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
