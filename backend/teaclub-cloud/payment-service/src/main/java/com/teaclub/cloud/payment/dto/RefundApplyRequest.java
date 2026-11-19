package com.teaclub.cloud.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "退款申请")
public class RefundApplyRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "退款金额不能为空")
    @Min(value = 1, message = "退款金额必须大于 0")
    @Schema(description = "本次退款金额（分）。服务端会校验不超过实付与已退差额")
    private Long refundAmount;

    @Schema(description = "退款原因")
    private String reason;
}
