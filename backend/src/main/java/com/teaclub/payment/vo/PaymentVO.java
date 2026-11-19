package com.teaclub.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "支付单")
public class PaymentVO {
    private Long id;
    private String paymentNo;
    private String bizType;
    private String bizNo;
    private Long storeId;
    private Long memberId;
    private String channel;
    @Schema(description = "渠道实现：MOCK / WECHAT_V3")
    private String provider;
    private Long amount;
    private String status;
    private String transactionId;
    private String codeUrl;
    private LocalDateTime successTime;
    private String failReason;
    private LocalDateTime createTime;
}
