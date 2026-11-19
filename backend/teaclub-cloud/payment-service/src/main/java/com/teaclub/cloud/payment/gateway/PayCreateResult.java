package com.teaclub.cloud.payment.gateway;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PayCreateResult {
    private boolean success;
    /** 渠道预支付 ID */
    private String prepayId;
    /** Native 二维码链接 */
    private String codeUrl;
    /** 小程序调起支付所需参数 */
    private Map<String, String> invokeParams;
    private String failReason;
}
