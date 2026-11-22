package com.teaclub.cloud.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 充值退卡申请。
 *
 * <p>只需要充值单号——应退多少现金、扣回多少赠送金全部由服务端根据充值单计算，
 * <b>不接受客户端传入任何金额</b>（与支付/下单的金额安全约定一致）。
 */
@Data
@Schema(description = "充值退卡申请")
public class RechargeRefundRequest {

    @NotBlank(message = "充值单号不能为空")
    @Schema(description = "要退的充值单号")
    private String rechargeNo;

    @Schema(description = "退卡原因")
    private String reason;
}
