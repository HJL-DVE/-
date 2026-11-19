package com.teaclub.payment.gateway;

import lombok.Builder;
import lombok.Data;

/**
 * 已验签并解密的通知载荷。
 */
@Data
@Builder
public class NotifyPayload {
    private String notifyId;
    private String eventType;
    private String outTradeNo;
    private String transactionId;
    private String tradeState;
    /** 实付金额（分） */
    private Long amount;
    private String merchantId;
    private String appId;
    private String payerOpenid;
    private String successTime;
}
