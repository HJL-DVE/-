package com.teaclub.cloud.payment.gateway;

import lombok.Builder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResult {
    private boolean success;
    /** SUCCESS / PROCESSING / CLOSED / ABNORMAL */
    private String status;
    private String refundId;
    private String failReason;
}
