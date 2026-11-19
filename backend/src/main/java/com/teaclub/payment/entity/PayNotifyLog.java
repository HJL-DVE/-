package com.teaclub.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_notify_log")
public class PayNotifyLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String notifyId;
    private String notifyType;
    private String eventType;
    private String outTradeNo;
    private String transactionId;
    private String headersJson;
    private String rawBody;
    private String decryptBody;
    private Integer verifyResult;
    private String processStatus;
    private String processMsg;
    private Integer retryCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
