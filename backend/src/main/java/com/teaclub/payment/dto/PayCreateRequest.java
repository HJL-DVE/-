package com.teaclub.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "发起支付请求")
public class PayCreateRequest {

    @NotBlank(message = "业务单号不能为空")
    private String bizNo;

    @NotBlank(message = "业务类型不能为空")
    @Pattern(regexp = "^(ORDER|RECHARGE)$", message = "业务类型只能是 ORDER 或 RECHARGE")
    private String bizType;

    @Schema(description = "支付渠道：WECHAT_JSAPI / WECHAT_NATIVE / MOCK")
    private String channel = "WECHAT_JSAPI";

    @Schema(description = "小程序 openid（JSAPI 必填）")
    private String openid;

    private String clientIp;
}
