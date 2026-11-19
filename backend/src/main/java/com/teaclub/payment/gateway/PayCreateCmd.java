package com.teaclub.payment.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayCreateCmd {
    /** 平台支付单号（即微信 out_trade_no） */
    private String paymentNo;
    /** 金额（分） */
    private Long amount;
    private String description;
    /** 小程序 openid（JSAPI 必填） */
    private String openid;
    /** 客户端 IP */
    private String clientIp;
    /** 过期秒数 */
    private Long expireSeconds;
}
