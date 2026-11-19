package com.teaclub.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "退款单")
public class RefundVO {
    private Long id;
    private String refundNo;
    private String paymentNo;
    private String bizType;
    private String bizNo;
    private Long storeId;
    private Long memberId;
    private String channel;
    @Schema(description = "退款总额（分）")
    private Long refundAmount;
    @Schema(description = "退回实付余额（分）")
    private Long refundPrincipalAmount;
    @Schema(description = "退回赠送余额（分）")
    private Long refundGiftAmount;
    @Schema(description = "微信原路退回现金（分）")
    private Long refundCashAmount;
    private String reason;
    private String status;
    private String wxRefundId;
    private LocalDateTime successTime;
    private String failReason;
    private LocalDateTime createTime;
}
