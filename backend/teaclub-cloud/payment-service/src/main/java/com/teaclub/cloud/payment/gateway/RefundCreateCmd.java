package com.teaclub.cloud.payment.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundCreateCmd {
    /** 平台退款单号 */
    private String refundNo;
    /** 原支付单号 */
    private String paymentNo;
    /** 原支付金额（分） */
    private Long totalAmount;
    /** 本次退款金额（分） */
    private Long refundAmount;
    private String reason;
}
