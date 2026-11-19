package com.teaclub.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "创建充值订单请求")
public class RechargeCreateRequest {

    @NotNull(message = "会员不能为空")
    private Long memberId;

    @NotNull(message = "门店不能为空")
    private Long storeId;

    @Schema(description = "充值方案 ID；自定义金额时为自定义方案 ID")
    private Long planId;

    @Schema(description = "自定义充值金额（分），仅自定义方案使用；未传则取方案金额")
    @Min(value = 1, message = "充值金额必须大于 0")
    private Long customAmount;

    @Schema(description = "支付渠道：MOCK / WECHAT_JSAPI / WECHAT_NATIVE")
    private String payChannel = "MOCK";

    @Schema(description = "备注")
    private String remark;
}
