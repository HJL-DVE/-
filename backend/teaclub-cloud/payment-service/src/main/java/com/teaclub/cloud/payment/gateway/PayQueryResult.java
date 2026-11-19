package com.teaclub.cloud.payment.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayQueryResult {
    /** SUCCESS / NOTPAY / CLOSED / REFUND / PAYERROR / UNKNOWN */
    private String tradeState;
    private String transactionId;
    private Long amount;
    private String failReason;
}
