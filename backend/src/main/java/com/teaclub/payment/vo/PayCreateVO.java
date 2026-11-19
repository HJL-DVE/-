package com.teaclub.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "支付下单结果")
public class PayCreateVO {
    private String paymentNo;
    private String provider;
    private String channel;
    @Schema(description = "支付金额（分），服务端计算")
    private Long amount;
    private String status;
    @Schema(description = "小程序调起支付参数")
    private Map<String, String> invokeParams;
    @Schema(description = "Native 二维码链接")
    private String codeUrl;
    @Schema(description = "提示：Mock 渠道需调用 Mock 通知接口完成支付")
    private String tip;
}
